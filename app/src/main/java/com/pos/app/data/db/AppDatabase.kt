package com.pos.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pos.app.data.db.dao.MenuGroupDao
import com.pos.app.data.db.dao.MenuItemDao
import com.pos.app.data.db.dao.OrderDao
import com.pos.app.data.db.dao.OrderItemDao
import com.pos.app.data.db.dao.TableDao
import com.pos.app.data.db.entity.MenuGroupEntity
import com.pos.app.data.db.entity.MenuItemEntity
import com.pos.app.data.db.entity.OrderEntity
import com.pos.app.data.db.entity.OrderItemEntity
import com.pos.app.data.db.entity.TableEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [MenuGroupEntity::class, MenuItemEntity::class, OrderEntity::class, OrderItemEntity::class, TableEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun menuGroupDao(): MenuGroupDao
    abstract fun menuItemDao(): MenuItemDao
    abstract fun orderDao(): OrderDao
    abstract fun orderItemDao(): OrderItemDao
    abstract fun tableDao(): TableDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val DEFAULT_MENU_GROUPS = listOf(
            MenuGroupEntity(code = "HOTPOT_BASE", name = "鍋底", sortOrder = 1),
            MenuGroupEntity(code = "MEAT", name = "肉類", sortOrder = 2),
            MenuGroupEntity(code = "SEAFOOD", name = "海鮮", sortOrder = 3),
            MenuGroupEntity(code = "VEGETABLE", name = "蔬菜", sortOrder = 4),
            MenuGroupEntity(code = "BEVERAGE", name = "飲料", sortOrder = 5),
            MenuGroupEntity(code = "OTHER", name = "其他", sortOrder = 6)
        )

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `menu_groups` (
                        `code` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(`code`)
                    )
                    """.trimIndent()
                )

                DEFAULT_MENU_GROUPS.forEach { group ->
                    val escapedCode = group.code.replace("'", "''")
                    val escapedName = group.name.replace("'", "''")
                    db.execSQL(
                        "INSERT OR IGNORE INTO `menu_groups` (`code`, `name`, `sortOrder`, `isActive`) VALUES ('$escapedCode', '$escapedName', ${group.sortOrder}, ${if (group.isActive) 1 else 0})"
                    )
                }

                db.execSQL("ALTER TABLE order_items ADD COLUMN menuGroupCode TEXT NOT NULL DEFAULT 'OTHER'")
                db.execSQL("ALTER TABLE order_items ADD COLUMN menuGroupName TEXT NOT NULL DEFAULT '其他'")
                db.execSQL(
                    """
                    UPDATE order_items
                    SET menuGroupCode = COALESCE(
                        (SELECT menu_items.category FROM menu_items WHERE menu_items.id = order_items.menuItemId),
                        'OTHER'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE order_items
                    SET menuGroupName = COALESCE(
                        (SELECT menu_groups.name FROM menu_groups WHERE menu_groups.code = order_items.menuGroupCode),
                        '其他'
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    seedDefaultMenuGroups(database.menuGroupDao())
                                    seedDefaultMenu(database.menuItemDao())
                                    seedDefaultTables(database.tableDao())
                                }
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }

        private suspend fun seedDefaultMenuGroups(dao: MenuGroupDao) {
            dao.insertAll(DEFAULT_MENU_GROUPS)
        }

        private suspend fun seedDefaultMenu(dao: MenuItemDao) {
            val defaults = listOf(
                MenuItemEntity(name = "鴛鴦鍋", price = 350.0, category = "HOTPOT_BASE", sortOrder = 1),
                MenuItemEntity(name = "麻辣鍋", price = 300.0, category = "HOTPOT_BASE", sortOrder = 2),
                MenuItemEntity(name = "清湯鍋", price = 250.0, category = "HOTPOT_BASE", sortOrder = 3),
                MenuItemEntity(name = "梅花豬肉片", price = 180.0, category = "MEAT", sortOrder = 1),
                MenuItemEntity(name = "五花肉", price = 160.0, category = "MEAT", sortOrder = 2),
                MenuItemEntity(name = "牛小排", price = 280.0, category = "MEAT", sortOrder = 3),
                MenuItemEntity(name = "鮮蝦", price = 220.0, category = "SEAFOOD", sortOrder = 1),
                MenuItemEntity(name = "透抽", price = 200.0, category = "SEAFOOD", sortOrder = 2),
                MenuItemEntity(name = "蛤蜊", price = 150.0, category = "SEAFOOD", sortOrder = 3),
                MenuItemEntity(name = "高麗菜", price = 60.0, category = "VEGETABLE", sortOrder = 1),
                MenuItemEntity(name = "茼蒿", price = 60.0, category = "VEGETABLE", sortOrder = 2),
                MenuItemEntity(name = "金針菇", price = 50.0, category = "VEGETABLE", sortOrder = 3),
                MenuItemEntity(name = "台灣啤酒", price = 60.0, category = "BEVERAGE", sortOrder = 1),
                MenuItemEntity(name = "可樂", price = 40.0, category = "BEVERAGE", sortOrder = 2),
                MenuItemEntity(name = "礦泉水", price = 30.0, category = "BEVERAGE", sortOrder = 3),
                MenuItemEntity(name = "白飯", price = 20.0, category = "OTHER", sortOrder = 1),
                MenuItemEntity(name = "沾醬", price = 10.0, category = "OTHER", sortOrder = 2)
            )
            dao.insertAll(defaults)
        }

        private suspend fun seedDefaultTables(dao: TableDao) {
            val defaults = (1..8).map { n ->
                TableEntity(tableName = "$n 號桌", sortOrder = n)
            }
            dao.insertAll(defaults)
        }
    }
}
