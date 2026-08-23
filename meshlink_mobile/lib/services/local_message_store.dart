import 'dart:async';
import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';
import '../models/sos_message.dart';
import '../models/relay_result.dart';

class LocalMessageStore {
  Database? _db;

  Future<Database> get database async {
    if (_db != null) return _db!;
    _db = await _initDb();
    return _db!;
  }

  Future<Database> _initDb() async {
    final dbPath = await getDatabasesPath();
    final pathStr = join(dbPath, 'meshlink_rescue.db');

    return await openDatabase(
      pathStr,
      version: 1,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE messages (
            messageId INTEGER PRIMARY KEY,
            senderIdHash INTEGER,
            senderIdStr TEXT,
            latitude REAL,
            longitude REAL,
            timestamp INTEGER,
            ttl INTEGER,
            hopCount INTEGER,
            battery INTEGER,
            severity INTEGER,
            receivedAt TEXT
          )
        ''');

        await db.execute('''
          CREATE TABLE relay_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            messageIdHex TEXT,
            status TEXT,
            inputTtl INTEGER,
            outputTtl INTEGER,
            inputHops INTEGER,
            outputHops INTEGER,
            timestamp TEXT,
            details TEXT
          )
        ''');
      },
    );
  }

  Future<bool> hasSeenMessage(int messageId) async {
    final db = await database;
    final res = await db.query(
      'messages',
      where: 'messageId = ?',
      whereArgs: [messageId],
      limit: 1,
    );
    return res.isNotEmpty;
  }

  Future<void> saveMessage(SosMessage message) async {
    final db = await database;
    await db.insert(
      'messages',
      message.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<SosMessage>> getAllMessages() async {
    final db = await database;
    final maps = await db.query('messages', orderBy: 'receivedAt DESC');
    return maps.map((m) => SosMessage.fromMap(m)).toList();
  }

  Future<void> logRelay(RelayResult result) async {
    final db = await database;
    await db.insert('relay_logs', {
      'messageIdHex': result.messageIdHex,
      'status': result.status.name,
      'inputTtl': result.inputTtl,
      'outputTtl': result.outputTtl,
      'inputHops': result.inputHops,
      'outputHops': result.outputHops,
      'timestamp': result.timestamp.toIso8601String(),
      'details': result.details,
    });
  }

  Future<List<RelayResult>> getRelayLogs() async {
    final db = await database;
    final maps = await db.query('relay_logs', orderBy: 'id DESC', limit: 100);
    return maps.map((m) {
      return RelayResult(
        messageIdHex: m['messageIdHex'] as String,
        status: RelayStatus.values.firstWhere(
          (e) => e.name == m['status'],
          orElse: () => RelayStatus.received,
        ),
        inputTtl: m['inputTtl'] as int,
        outputTtl: m['outputTtl'] as int,
        inputHops: m['inputHops'] as int,
        outputHops: m['outputHops'] as int,
        timestamp: DateTime.parse(m['timestamp'] as String),
        details: m['details'] as String?,
      );
    }).toList();
  }

  Future<void> clearAll() async {
    final db = await database;
    await db.delete('messages');
    await db.delete('relay_logs');
  }
}
