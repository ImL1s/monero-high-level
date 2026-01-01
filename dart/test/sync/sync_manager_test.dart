import 'dart:typed_data';
import 'package:test/test.dart';
import 'package:monero_dart/src/sync/sync_manager.dart';
import 'package:monero_dart/src/transaction/models.dart';
import 'package:monero_dart/src/transaction/scanner.dart';

void main() {
  group('SyncState', () {
    test('SyncStateSyncing calculates progress', () {
      final state = SyncStateSyncing(
        currentHeight: 500,
        targetHeight: 1000,
        blocksProcessed: 500,
        startTime: DateTime.now().millisecondsSinceEpoch - 10000, // 10 seconds ago
      );
      
      expect(state.progress, equals(0.5));
      expect(state.blocksPerSecond, greaterThan(0));
    });
    
    test('SyncStateIdle equality', () {
      expect(SyncStateIdle(), equals(SyncStateIdle()));
    });
    
    test('SyncStateSynced equality', () {
      expect(SyncStateSynced(100), equals(SyncStateSynced(100)));
      expect(SyncStateSynced(100), isNot(equals(SyncStateSynced(200))));
    });
    
    test('SyncStateError contains message', () {
      final error = SyncStateError('Test error', Exception('cause'));
      expect(error.message, equals('Test error'));
      expect(error.cause, isNotNull);
    });
  });
  
  group('SyncConfig', () {
    test('has sensible defaults', () {
      const config = SyncConfig();
      expect(config.batchSize, equals(100));
      expect(config.batchDelayMs, equals(100));
      expect(config.confirmations, equals(10));
      expect(config.autoRetry, isTrue);
      expect(config.maxRetries, equals(3));
    });
    
    test('can be customized', () {
      const config = SyncConfig(
        batchSize: 50,
        batchDelayMs: 200,
        confirmations: 5,
        autoRetry: false,
      );
      expect(config.batchSize, equals(50));
      expect(config.batchDelayMs, equals(200));
      expect(config.autoRetry, isFalse);
    });
  });
  
  group('BlockData', () {
    test('equality based on height and hash', () {
      final hash1 = List<int>.filled(32, 0x01);
      final hash2 = List<int>.filled(32, 0x01);
      final hash3 = List<int>.filled(32, 0x02);
      
      final block1 = BlockData(
        height: 100,
        hash: hash1,
        timestamp: 1234567890,
        prevHash: List<int>.filled(32, 0),
        transactions: [],
      );
      
      final block2 = BlockData(
        height: 100,
        hash: hash2,
        timestamp: 1234567890,
        prevHash: List<int>.filled(32, 0),
        transactions: [],
      );
      
      final block3 = BlockData(
        height: 100,
        hash: hash3,
        timestamp: 1234567890,
        prevHash: List<int>.filled(32, 0),
        transactions: [],
      );
      
      expect(block1, equals(block2));
      expect(block1, isNot(equals(block3)));
    });
  });
  
  group('SyncEvent', () {
    test('ProgressUpdateEvent stores values', () {
      final event = ProgressUpdateEvent(500, 1000);
      expect(event.current, equals(500));
      expect(event.target, equals(1000));
    });
    
    test('OutputReceivedEvent stores output', () {
      final output = OwnedOutput(
        txHash: Uint8List.fromList(List<int>.filled(32, 0xAB)),
        outputIndex: 0,
        globalIndex: 12345,
        amount: 1000000000,
        publicKey: Uint8List.fromList(List<int>.filled(32, 0xCD)),
        blockHeight: 100,
        timestamp: DateTime.now().millisecondsSinceEpoch ~/ 1000,
        subaddressMajor: 0,
        subaddressMinor: 1,
      );
      
      final event = OutputReceivedEvent(output);
      expect(event.output, equals(output));
    });
    
    test('ReorgDetectedEvent stores heights', () {
      final event = ReorgDetectedEvent(100, 150);
      expect(event.fromHeight, equals(100));
      expect(event.toHeight, equals(150));
    });
  });
}

// Mock implementations for integration tests
class MockBlockProvider implements BlockProvider {
  int height = 1000;
  final Map<int, BlockData> blocks = {};
  
  MockBlockProvider() {
    // Generate mock blocks
    for (var h = 0; h <= height; h++) {
      blocks[h] = BlockData(
        height: h,
        hash: List<int>.filled(32, h % 256),
        timestamp: 1700000000 + h * 120,
        prevHash: h > 0 ? List<int>.filled(32, (h - 1) % 256) : List<int>.filled(32, 0),
        transactions: [],
      );
    }
  }
  
  @override
  Future<int> getHeight() async => height;
  
  @override
  Future<BlockData> getBlockByHeight(int height) async {
    final block = blocks[height];
    if (block == null) {
      throw ArgumentError('Block not found: $height');
    }
    return block;
  }
  
  @override
  Future<List<BlockData>> getBlocksByRange(int startHeight, int endHeight) async {
    return [for (var h = startHeight; h <= endHeight; h++) blocks[h]!];
  }
}

class MockWalletStorage implements WalletStorage {
  int lastSyncedHeight = -1;
  final Map<int, List<int>> blockHashes = {};
  final List<OwnedOutput> outputs = [];
  final Set<String> spentKeyImages = {};
  
  @override
  Future<int> getLastSyncedHeight() async => lastSyncedHeight;
  
  @override
  Future<void> setLastSyncedHeight(int height) async {
    lastSyncedHeight = height;
  }
  
  @override
  Future<List<int>?> getBlockHash(int height) async => blockHashes[height];
  
  @override
  Future<void> setBlockHash(int height, List<int> hash) async {
    blockHashes[height] = hash;
  }
  
  @override
  Future<void> saveOutput(OwnedOutput output) async {
    outputs.add(output);
  }
  
  @override
  Future<void> markOutputSpent(List<int> keyImage) async {
    spentKeyImages.add(keyImage.map((b) => b.toRadixString(16).padLeft(2, '0')).join());
  }
  
  @override
  Future<void> rollbackToHeight(int height) async {
    lastSyncedHeight = height;
    blockHashes.removeWhere((h, _) => h > height);
    outputs.removeWhere((o) => o.blockHeight > height);
  }
}

ViewKeyScanner createMockScanner() {
  // Create scanner with dummy keys
  final viewPubKey = Uint8List.fromList(List<int>.filled(32, 0x01));
  final viewSecretKey = Uint8List.fromList(List<int>.filled(32, 0x02));
  final spendPubKey = Uint8List.fromList(List<int>.filled(32, 0x03));
  return ViewKeyScanner(
    viewPublicKey: viewPubKey,
    viewSecretKey: viewSecretKey,
    spendPublicKey: spendPubKey,
  );
}
