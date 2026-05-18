# Backup

Kalium exposes two backup-oriented scopes:

```kotlin
session.backup
session.multiPlatformBackup
```

Source anchors:

- `logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/backup/BackupScope.kt`
- `logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/backup/MultiPlatformBackupScope.kt`
- `sample/samples/src/commonMain/kotlin/samples/backup/BackupSamples.kt`

## BackupScope

Public APIs:

```kotlin
session.backup.create
session.backup.verify
session.backup.restore
session.backup.createUnEncryptedCopy
session.backup.backupAndUploadCryptoState
session.backup.setLastDeviceId
session.backup.restoreCryptoState
```

Capabilities:

- create local backup
- verify backup
- restore backup
- create unencrypted/obfuscated copy for support/debug flows
- backup and upload crypto state
- restore crypto state

## Multi-platform backup

`session.multiPlatformBackup` handles backup data that can be imported/exported across platforms. The sample shows paged import handling for:

- conversations
- users
- messages
- reactions

## Import result handling

Sample pattern:

```kotlin
when (importResult) {
    BackupImportResult.Failure.MissingOrWrongPassphrase -> {
        // user entered wrong passphrase
    }
    BackupImportResult.Failure.ParsingFailure -> {
        // invalid or unsupported backup
    }
    is BackupImportResult.Failure.UnknownError -> {
        // inspect importResult.message
    }
    is BackupImportResult.Failure.UnzippingError -> {
        // unzip implementation failed
    }
    is BackupImportResult.Success -> {
        val pager = importResult.pager
        // iterate conversations/users/messages/reactions pages
    }
}
```

## Paged import

```kotlin
val importPager = importResult.pager
val totalPages = importPager.totalPagesCount
var processedPages = 0

while (importPager.messagesPager.hasMorePages()) {
    val page = importPager.messagesPager.nextPage()
    processedPages++
    // map each item and insert into app/local database through Kalium-backed flow
}
```

## Practical notes

- Always show progress for large imports/exports.
- Treat backup passphrase errors differently from parsing/corruption errors.
- Keep backup files in app-controlled storage.
- Do not log backup contents, passphrases, or cryptographic material.
- Crypto-state backup/restore is security-sensitive; gate it behind clear UX and account/device checks.
