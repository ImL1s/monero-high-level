/// Monero protocol constants.
///
/// These values are from the Monero reference implementation
/// and should not be changed without careful consideration.
library;

/// Current Monero protocol version
const int protocolVersion = 18;

/// Ring signature size (16 since protocol v8)
const int ringSize = 16;

/// Number of decoy outputs in a ring
const int numDecoys = 15;

/// Block unlock time (10 blocks for standard, 60 for coinbase)
const int standardUnlockTime = 10;
const int coinbaseUnlockTime = 60;

/// Mnemonic word count
const int mnemonicWordCount = 25;
const int mnemonicSeedBytes = 32;

/// Consolidated Monero constants for convenience.
class MoneroConstants {
  MoneroConstants._();

  /// Ring size
  static const int ringSize = 16;

  /// Maximum inputs per transaction
  static const int maxInputsPerTx = 128;

  /// Maximum outputs per transaction
  static const int maxOutputsPerTx = 16;

  /// Standard unlock time
  static const int standardUnlockTime = 10;

  /// Coinbase unlock time
  static const int coinbaseUnlockTime = 60;

  /// XMR to atomic units factor
  static const int xmrFactor = 1000000000000;

  /// Dust threshold
  static const int dustThreshold = 10000;

  /// Gamma shape parameter for decoy selection
  static const double gammaShape = 19.28;

  /// Gamma scale parameter for decoy selection
  static const double gammaScale = 1.61;
}

/// Address prefixes
class AddressPrefix {
  AddressPrefix._();

  // Mainnet
  static const int mainnetStandard = 18;    // '4'
  static const int mainnetSubaddress = 42;  // '8'
  static const int mainnetIntegrated = 19;  // '4'

  // Stagenet
  static const int stagenetStandard = 24;   // '5'
  static const int stagenetSubaddress = 36; // '7'
  static const int stagenetIntegrated = 25; // '5'

  // Testnet
  static const int testnetStandard = 53;    // '9'
  static const int testnetSubaddress = 63;  // 'A'
  static const int testnetIntegrated = 54;  // '9'
}

/// Transaction fee constants
class FeeConstants {
  FeeConstants._();

  /// Fee per kB base (in atomic units)
  static const int feePerKbBase = 1000000000; // 0.001 XMR

  /// Fee multiplier tiers
  static const int priorityDefault = 1;
  static const int priorityLow = 1;
  static const int priorityMedium = 4;
  static const int priorityHigh = 20;
  static const int priorityHighest = 166;

  /// Minimum fee
  static const int minimumFee = 10000; // 0.00000001 XMR
}

/// Crypto constants
class CryptoConstants {
  CryptoConstants._();

  /// Ed25519 key length
  static const int keyLength = 32;

  /// Keccak-256 output length
  static const int hashLength = 32;

  /// Payment ID length
  static const int paymentIdLength = 8;

  /// Encrypted payment ID length
  static const int encryptedPaymentIdLength = 8;

  /// View tag length
  static const int viewTagLength = 1;
}

/// RingCT constants
class RingCTConstants {
  RingCTConstants._();

  /// Bulletproof+ max outputs
  static const int bulletproofPlusMaxOutputs = 16;

  /// CLSAG signature type
  static const int clsagVersion = 4;
}

/// Network defaults
class NetworkDefaults {
  NetworkDefaults._();

  /// Default mainnet daemon port
  static const int mainnetPort = 18081;
  static const int mainnetRpcPort = 18082;

  /// Default stagenet daemon port
  static const int stagenetPort = 38081;
  static const int stagenetRpcPort = 38082;

  /// Default testnet daemon port
  static const int testnetPort = 28081;
  static const int testnetRpcPort = 28082;
}

/// Atomic units conversion
class AtomicUnits {
  AtomicUnits._();

  /// 1 XMR = 10^12 atomic units (piconero)
  static const int xmrFactor = 1000000000000;

  /// Convert XMR to atomic units
  static int fromXmr(double xmr) => (xmr * xmrFactor).round();

  /// Convert atomic units to XMR
  static double toXmr(int atomic) => atomic / xmrFactor;
}
