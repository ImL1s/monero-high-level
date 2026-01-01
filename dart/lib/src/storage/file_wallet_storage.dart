/// Conditional export for file-based wallet storage.
///
/// On IO platforms this provides a working file-backed implementation.
/// On non-IO platforms it throws [UnsupportedError].
library;

export 'file_wallet_storage_stub.dart'
    if (dart.library.io) 'file_wallet_storage_io.dart';
