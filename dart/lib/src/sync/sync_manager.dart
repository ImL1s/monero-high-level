import 'dart:async';
import 'package:monero_dart/src/transaction/models.dart';
import 'package:monero_dart/src/transaction/scanner.dart';

/// Sync state representing the current synchronization status
sealed class SyncState {}

/// Not started syncing
class SyncStateIdle extends SyncState {
  @override
  bool operator ==(Object other) => other is SyncStateIdle;
  
  @override
  int get hashCode => 0;
}

/// Currently syncing blocks
class SyncStateSyncing extends SyncState {
  final int currentHeight;
  final int targetHeight;
  final int blocksProcessed;
  final int startTime;
  
  SyncStateSyncing({
    required this.currentHeight,
    required this.targetHeight,
    required this.blocksProcessed,
    int? startTime,
  }) : startTime = startTime ?? DateTime.now().millisecondsSinceEpoch;
  
  double get progress => targetHeight > 0 ? currentHeight / targetHeight : 0.0;
  
  double get blocksPerSecond {
    final elapsed = (DateTime.now().millisecondsSinceEpoch - startTime) / 1000;
    return elapsed > 0 ? blocksProcessed / elapsed : 0.0;
  }
  
  double get estimatedSecondsRemaining {
    final remaining = targetHeight - currentHeight;
    return blocksPerSecond > 0 ? remaining / blocksPerSecond : double.infinity;
  }
  
  @override
  bool operator ==(Object other) =>
      other is SyncStateSyncing &&
      other.currentHeight == currentHeight &&
      other.targetHeight == targetHeight;
  
  @override
  int get hashCode => Object.hash(currentHeight, targetHeight);
}

/// Sync completed
class SyncStateSynced extends SyncState {
  final int height;
  
  SyncStateSynced(this.height);
  
  @override
  bool operator ==(Object other) =>
      other is SyncStateSynced && other.height == height;
  
  @override
  int get hashCode => height.hashCode;
}

/// Sync error occurred
class SyncStateError extends SyncState {
  final String message;
  final Object? cause;
  
  SyncStateError(this.message, [this.cause]);
  
  @override
  bool operator ==(Object other) =>
      other is SyncStateError && other.message == message;
  
  @override
  int get hashCode => message.hashCode;
}

/// Events emitted during synchronization
sealed class SyncEvent {}

/// New block processed
class BlockProcessedEvent extends SyncEvent {
  final int height;
  final List<int> hash;
  
  BlockProcessedEvent(this.height, this.hash);
}

/// New transaction found
class TransactionFoundEvent extends SyncEvent {
  final Transaction tx;
  
  TransactionFoundEvent(this.tx);
}

/// New output received
class OutputReceivedEvent extends SyncEvent {
  final OwnedOutput output;
  
  OutputReceivedEvent(this.output);
}

/// Output spent
class OutputSpentEvent extends SyncEvent {
  final List<int> keyImage;
  
  OutputSpentEvent(this.keyImage);
}

/// Chain reorganization detected
class ReorgDetectedEvent extends SyncEvent {
  final int fromHeight;
  final int toHeight;
  
  ReorgDetectedEvent(this.fromHeight, this.toHeight);
}

/// Sync progress update
class ProgressUpdateEvent extends SyncEvent {
  final int current;
  final int target;
  
  ProgressUpdateEvent(this.current, this.target);
}

/// Configuration for sync manager
class SyncConfig {
  /// Number of blocks to fetch in parallel
  final int batchSize;
  
  /// Delay between batches (ms)
  final int batchDelayMs;
  
  /// Number of confirmations required
  final int confirmations;
  
  /// Auto-restart on error
  final bool autoRetry;
  
  /// Maximum retry attempts
  final int maxRetries;
  
  /// Retry delay (ms)
  final int retryDelayMs;
  
  const SyncConfig({
    this.batchSize = 100,
    this.batchDelayMs = 100,
    this.confirmations = 10,
    this.autoRetry = true,
    this.maxRetries = 3,
    this.retryDelayMs = 5000,
  });
}

/// Block data from daemon
class BlockData {
  final int height;
  final List<int> hash;
  final int timestamp;
  final List<int> prevHash;
  final List<Transaction> transactions;
  
  BlockData({
    required this.height,
    required this.hash,
    required this.timestamp,
    required this.prevHash,
    required this.transactions,
  });
  
  @override
  bool operator ==(Object other) =>
      other is BlockData &&
      other.height == height &&
      _listEquals(other.hash, hash);
  
  @override
  int get hashCode => Object.hash(height, Object.hashAll(hash));
}

bool _listEquals<T>(List<T> a, List<T> b) {
  if (a.length != b.length) return false;
  for (var i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
  }
  return true;
}

/// Interface for block data provider (implemented by DaemonClient)
abstract class BlockProvider {
  Future<int> getHeight();
  Future<BlockData> getBlockByHeight(int height);
  Future<List<BlockData>> getBlocksByRange(int startHeight, int endHeight);
}

/// Interface for wallet storage
abstract class WalletStorage {
  Future<int> getLastSyncedHeight();
  Future<void> setLastSyncedHeight(int height);
  Future<List<int>?> getBlockHash(int height);
  Future<void> setBlockHash(int height, List<int> hash);
  Future<void> saveOutput(OwnedOutput output);
  Future<void> markOutputSpent(List<int> keyImage);
  Future<void> rollbackToHeight(int height);
}

/// Manages blockchain synchronization for a wallet.
/// 
/// Coordinates block fetching, transaction scanning, and state management.
class SyncManager {
  final ViewKeyScanner _scanner;
  final BlockProvider _blockProvider;
  final WalletStorage _storage;
  final SyncConfig _config;
  
  final _stateController = StreamController<SyncState>.broadcast();
  final _eventController = StreamController<SyncEvent>.broadcast();
  
  SyncState _currentState = SyncStateIdle();
  bool _isRunning = false;
  int _retryCount = 0;
  
  SyncManager({
    required ViewKeyScanner scanner,
    required BlockProvider blockProvider,
    required WalletStorage storage,
    SyncConfig config = const SyncConfig(),
  })  : _scanner = scanner,
        _blockProvider = blockProvider,
        _storage = storage,
        _config = config;
  
  /// Stream of sync state changes
  Stream<SyncState> get stateStream => _stateController.stream;
  
  /// Stream of sync events
  Stream<SyncEvent> get eventStream => _eventController.stream;
  
  /// Current sync state
  SyncState get state => _currentState;
  
  /// Whether currently syncing
  bool get isSyncing => _currentState is SyncStateSyncing;
  
  /// Start synchronization
  Future<void> start() async {
    if (_isRunning) return;
    _isRunning = true;
    
    try {
      await _sync();
    } catch (e) {
      await _handleError(e);
    }
  }
  
  /// Stop synchronization
  void stop() {
    _isRunning = false;
    _setState(SyncStateIdle());
  }
  
  /// Dispose resources
  void dispose() {
    stop();
    _stateController.close();
    _eventController.close();
  }
  
  void _setState(SyncState state) {
    _currentState = state;
    _stateController.add(state);
  }
  
  void _emitEvent(SyncEvent event) {
    _eventController.add(event);
  }
  
  Future<void> _sync() async {
    final startHeight = await _storage.getLastSyncedHeight() + 1;
    var targetHeight = await _blockProvider.getHeight();
    
    if (startHeight > targetHeight) {
      _setState(SyncStateSynced(targetHeight));
      return;
    }
    
    final startTime = DateTime.now().millisecondsSinceEpoch;
    var blocksProcessed = 0;
    var currentHeight = startHeight;
    
    _setState(SyncStateSyncing(
      currentHeight: currentHeight,
      targetHeight: targetHeight,
      blocksProcessed: blocksProcessed,
      startTime: startTime,
    ));
    
    while (currentHeight <= targetHeight && _isRunning) {
      // Check for reorg before processing
      if (currentHeight > 0) {
        final reorgHeight = await _checkForReorg(currentHeight - 1);
        if (reorgHeight != null) {
          currentHeight = await _handleReorg(reorgHeight);
          continue;
        }
      }
      
      // Fetch batch of blocks
      final endHeight = currentHeight + _config.batchSize - 1;
      final clampedEnd = endHeight > targetHeight ? targetHeight : endHeight;
      final blocks = await _blockProvider.getBlocksByRange(currentHeight, clampedEnd);
      
      // Process each block
      for (final block in blocks) {
        if (!_isRunning) break;
        
        await _processBlock(block);
        blocksProcessed++;
        currentHeight = block.height + 1;
        
        _setState(SyncStateSyncing(
          currentHeight: currentHeight,
          targetHeight: targetHeight,
          blocksProcessed: blocksProcessed,
          startTime: startTime,
        ));
        _emitEvent(ProgressUpdateEvent(currentHeight, targetHeight));
      }
      
      // Update target height (blockchain may have grown)
      final newTargetHeight = await _blockProvider.getHeight();
      if (newTargetHeight > targetHeight) {
        targetHeight = newTargetHeight;
        _setState(SyncStateSyncing(
          currentHeight: currentHeight,
          targetHeight: newTargetHeight,
          blocksProcessed: blocksProcessed,
          startTime: startTime,
        ));
      }
      
      // Small delay between batches
      if (_config.batchDelayMs > 0 && currentHeight <= targetHeight) {
        await Future<void>.delayed(Duration(milliseconds: _config.batchDelayMs));
      }
    }
    
    if (_isRunning) {
      _retryCount = 0;
      _setState(SyncStateSynced(currentHeight - 1));
    }
  }
  
  Future<void> _processBlock(BlockData block) async {
    // Save block hash for reorg detection
    await _storage.setBlockHash(block.height, block.hash);
    
    // Scan each transaction
    for (final tx in block.transactions) {
      final ownedOutputs = _scanner.scanTransaction(tx);
      
      if (ownedOutputs.isNotEmpty) {
        _emitEvent(TransactionFoundEvent(tx));
        
        for (final scanned in ownedOutputs) {
          if (scanned.isOwned) {
            final owned = OwnedOutput(
              txHash: tx.hash,
              outputIndex: scanned.output.index,
              globalIndex: scanned.output.globalIndex,
              amount: scanned.amount ?? 0,
              publicKey: scanned.output.target.key,
              blockHeight: block.height,
              timestamp: block.timestamp,
              subaddressMajor: scanned.subaddressIndex?.major ?? 0,
              subaddressMinor: scanned.subaddressIndex?.minor ?? 0,
            );
            await _storage.saveOutput(owned);
            _emitEvent(OutputReceivedEvent(owned));
          }
        }
      }
      
      // Check for spent outputs (key images in inputs)
      for (final input in tx.inputs) {
        await _storage.markOutputSpent(input.keyImage);
        _emitEvent(OutputSpentEvent(input.keyImage));
      }
    }
    
    // Update last synced height
    await _storage.setLastSyncedHeight(block.height);
    _emitEvent(BlockProcessedEvent(block.height, block.hash));
  }
  
  /// Check for blockchain reorganization
  /// Returns the height where reorg occurred, or null if no reorg
  Future<int?> _checkForReorg(int height) async {
    final storedHash = await _storage.getBlockHash(height);
    if (storedHash == null) return null;
    
    final block = await _blockProvider.getBlockByHeight(height);
    
    if (!_listEquals(block.hash, storedHash)) {
      // Reorg detected, find the fork point
      var checkHeight = height - 1;
      while (checkHeight > 0) {
        final stored = await _storage.getBlockHash(checkHeight);
        if (stored == null) break;
        final current = await _blockProvider.getBlockByHeight(checkHeight);
        if (_listEquals(current.hash, stored)) {
          return checkHeight + 1; // Fork point
        }
        checkHeight--;
      }
      return 0; // Complete reorg from genesis
    }
    
    return null;
  }
  
  /// Handle blockchain reorganization
  /// Returns the height to restart syncing from
  Future<int> _handleReorg(int reorgHeight) async {
    final currentHeight = await _storage.getLastSyncedHeight();
    _emitEvent(ReorgDetectedEvent(reorgHeight, currentHeight));
    
    // Rollback storage to before the reorg
    await _storage.rollbackToHeight(reorgHeight - 1);
    
    return reorgHeight;
  }
  
  Future<void> _handleError(Object error) async {
    _setState(SyncStateError(error.toString(), error));
    
    if (_config.autoRetry && _retryCount < _config.maxRetries) {
      _retryCount++;
      await Future<void>.delayed(Duration(milliseconds: _config.retryDelayMs));
      if (_isRunning) {
        await start();
      }
    }
  }
}
