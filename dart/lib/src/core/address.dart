import 'dart:typed_data';

import '../crypto/base58.dart';
import '../constants.dart';

/// Network type enumeration.
enum Network {
  mainnet(
    standardPrefix: AddressPrefix.mainnetStandard,
    subaddressPrefix: AddressPrefix.mainnetSubaddress,
    integratedPrefix: AddressPrefix.mainnetIntegrated,
  ),
  stagenet(
    standardPrefix: AddressPrefix.stagenetStandard,
    subaddressPrefix: AddressPrefix.stagenetSubaddress,
    integratedPrefix: AddressPrefix.stagenetIntegrated,
  ),
  testnet(
    standardPrefix: AddressPrefix.testnetStandard,
    subaddressPrefix: AddressPrefix.testnetSubaddress,
    integratedPrefix: AddressPrefix.testnetIntegrated,
  );

  final int standardPrefix;
  final int subaddressPrefix;
  final int integratedPrefix;

  const Network({
    required this.standardPrefix,
    required this.subaddressPrefix,
    required this.integratedPrefix,
  });
}

/// Address type enumeration.
enum AddressType {
  standard,
  subaddress,
  integrated,
}

/// Monero address representation and utilities.
///
/// Address Types (Mainnet):
/// - Standard: prefix 18 (starts with '4')
/// - Subaddress: prefix 42 (starts with '8')
/// - Integrated: prefix 19 (starts with '4', includes payment ID)
///
/// Address Structure:
/// [1 byte prefix][32 bytes public spend key][32 bytes public view key]
/// [optional 8 bytes payment ID][4 bytes checksum]
class MoneroAddress {
  /// Raw Base58 encoded address string
  final String rawAddress;

  /// Base58 encoded address string.
  String get address => rawAddress;

  /// Network type
  final Network network;

  /// Address type
  final AddressType type;

  /// Public spend key (32 bytes)
  final Uint8List publicSpendKey;

  /// Public view key (32 bytes)
  final Uint8List publicViewKey;

  /// Payment ID (8 bytes, only for integrated addresses)
  final Uint8List? paymentId;

  const MoneroAddress({
    required this.rawAddress,
    required this.network,
    required this.type,
    required this.publicSpendKey,
    required this.publicViewKey,
    this.paymentId,
  });

  /// Parse a Monero address string.
  ///
  /// [address] Base58 encoded address
  /// Returns parsed MoneroAddress
  /// Throws [FormatException] if address is invalid
  factory MoneroAddress.parse(String address) {
    if (address.isEmpty) {
      throw const FormatException('Address cannot be empty');
    }

    final Uint8List decoded;
    try {
      decoded = Base58.decode(address);
    } catch (e) {
      throw FormatException('Invalid Base58 encoding: $e');
    }

    if (decoded.isEmpty) {
      throw const FormatException('Decoded address is empty');
    }

    final prefix = decoded[0];
    final (network, type) = _determineNetworkAndType(prefix);

    switch (type) {
      case AddressType.standard:
      case AddressType.subaddress:
        if (decoded.length != 65) {
          throw FormatException(
            'Invalid standard address size: ${decoded.length}',
          );
        }
        return MoneroAddress(
          rawAddress: address,
          network: network,
          type: type,
          publicSpendKey: decoded.sublist(1, 33),
          publicViewKey: decoded.sublist(33, 65),
        );

      case AddressType.integrated:
        if (decoded.length != 73) {
          throw FormatException(
            'Invalid integrated address size: ${decoded.length}',
          );
        }
        return MoneroAddress(
          rawAddress: address,
          network: network,
          type: type,
          publicSpendKey: decoded.sublist(1, 33),
          publicViewKey: decoded.sublist(33, 65),
          paymentId: decoded.sublist(65, 73),
        );
    }
  }

  /// Create a standard address from keys.
  factory MoneroAddress.fromKeys({
    required Uint8List publicSpendKey,
    required Uint8List publicViewKey,
    Network network = Network.mainnet,
  }) {
    if (publicSpendKey.length != 32) {
      throw ArgumentError('Public spend key must be 32 bytes');
    }
    if (publicViewKey.length != 32) {
      throw ArgumentError('Public view key must be 32 bytes');
    }

    final data = Uint8List(65)
      ..[0] = network.standardPrefix
      ..setAll(1, publicSpendKey)
      ..setAll(33, publicViewKey);

    final encoded = Base58.encode(data);

    return MoneroAddress(
      rawAddress: encoded,
      network: network,
      type: AddressType.standard,
      publicSpendKey: publicSpendKey,
      publicViewKey: publicViewKey,
    );
  }

  /// Create an integrated address with payment ID.
  factory MoneroAddress.createIntegrated({
    required MoneroAddress standardAddress,
    required Uint8List paymentId,
  }) {
    if (standardAddress.type != AddressType.standard) {
      throw ArgumentError('Source must be standard address');
    }
    if (paymentId.length != 8) {
      throw ArgumentError('Payment ID must be 8 bytes');
    }

    final data = Uint8List(73)
      ..[0] = standardAddress.network.integratedPrefix
      ..setAll(1, standardAddress.publicSpendKey)
      ..setAll(33, standardAddress.publicViewKey)
      ..setAll(65, paymentId);

    final encoded = Base58.encode(data);

    return MoneroAddress(
      rawAddress: encoded,
      network: standardAddress.network,
      type: AddressType.integrated,
      publicSpendKey: standardAddress.publicSpendKey,
      publicViewKey: standardAddress.publicViewKey,
      paymentId: paymentId,
    );
  }

  /// Check if this is a subaddress.
  bool get isSubaddress => type == AddressType.subaddress;

  /// Check if this has an integrated payment ID.
  bool get isIntegrated => type == AddressType.integrated;

  static (Network, AddressType) _determineNetworkAndType(int prefix) {
    return switch (prefix) {
      AddressPrefix.mainnetStandard => (Network.mainnet, AddressType.standard),
      AddressPrefix.mainnetSubaddress => (
        Network.mainnet,
        AddressType.subaddress
      ),
      AddressPrefix.mainnetIntegrated => (
        Network.mainnet,
        AddressType.integrated
      ),
      AddressPrefix.stagenetStandard => (
        Network.stagenet,
        AddressType.standard
      ),
      AddressPrefix.stagenetSubaddress => (
        Network.stagenet,
        AddressType.subaddress
      ),
      AddressPrefix.stagenetIntegrated => (
        Network.stagenet,
        AddressType.integrated
      ),
      AddressPrefix.testnetStandard => (Network.testnet, AddressType.standard),
      AddressPrefix.testnetSubaddress => (
        Network.testnet,
        AddressType.subaddress
      ),
      AddressPrefix.testnetIntegrated => (
        Network.testnet,
        AddressType.integrated
      ),
      _ => throw FormatException('Unknown address prefix: $prefix'),
    };
  }

  @override
  bool operator ==(Object other) {
    if (other is! MoneroAddress) return false;
    return rawAddress == other.rawAddress;
  }

  @override
  int get hashCode => rawAddress.hashCode;

  @override
  String toString() => rawAddress;
}
