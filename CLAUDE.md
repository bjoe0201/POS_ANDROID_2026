# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run a single test class
./gradlew test --tests "com.pos.app.ExampleUnitTest"

# Lint
./gradlew lint
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Architecture

**Package:** `com.pos.app` | **Min SDK:** 29 (Android 10) | **Target SDK:** 35

### Layer Structure

```
data/
  datastore/SettingsDataStore.kt   — PIN hash (SHA-256) via Jetpack DataStore
  db/
    entity/                        — Room entities (4 tables)
    dao/                           — Room DAOs
    AppDatabase.kt                 — singleton; seeds default menu + 8 tables on first create
  repository/                      — single source of truth; injected into ViewModels via Hilt
ui/
  navigation/NavGraph.kt           — root nav: Login → Home; Home wraps bottom-tab nested nav
  login / order / menu / table / report / settings / theme
util/BackupManager.kt              — JSON + CSV import/export via SAF (Storage Access Framework)
```

### Navigation Flow

`LoginScreen` → (PIN verified) → `HomeWithBottomNav` (4 tabs) → Settings (pushed on top)

Bottom tabs: **記帳** (`OrderScreen`) · **品項設定** (`MenuManagementScreen`) · **桌號設定** (`TableSettingScreen`) · **報表** (`ReportScreen`)

Settings is reachable via icon from 記帳 and 報表 tabs only.

### Database Schema

| Table | Key fields |
|-------|-----------|
| `menu_items` | id, name, price, category, isAvailable, sortOrder |
| `orders` | id, **tableId** (FK→tables), **tableName** (snapshot), remark, createdAt, closedAt, status |
| `order_items` | id, orderId, menuItemId, name/price (snapshot), quantity |
| `tables` | id, tableName (≤20 chars), seats, remark, isActive, sortOrder |

`OrderEntity.tableName` is a snapshot — it stays readable even if the table is later renamed or deleted.

### DI (Hilt)

All DAOs, Repositories, and `SettingsDataStore` are `@Singleton` provided in `AppModule`. ViewModels use `@HiltViewModel`. The entry point is `POSApplication` (`@HiltAndroidApp`) and `MainActivity` (`@AndroidEntryPoint`).

### Key Constants

- `CATEGORIES` list (order + display name) lives in `ui/order/OrderViewModel.kt` and is imported by menu and other screens.
- Default PIN: `1234` (SHA-256 hashed). 3 failed attempts → 30-second lockout.
- Default tables: 8 (seeded as "1號桌"–"8號桌"); adjustable via TableSettingScreen CRUD.

### Backup / Export

`BackupManager` (util) uses Android SAF (`ActivityResultContracts.CreateDocument` / `OpenDocument`). JSON export includes menu items + all orders + order items. CSV export is orders only. Import replaces all menu items (orders are not overwritten). Export/import UI lives in `ReportScreen`; PIN change lives in `SettingsScreen`.

## Plan Document

Full feature spec: `PLANS/plan-hotPotPosApp.prompt.md`
