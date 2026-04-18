package com.pos.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pos.app.data.db.dao.MenuItemDao
import com.pos.app.data.db.dao.OrderDao
import com.pos.app.data.db.dao.OrderItemDao
import com.pos.app.data.db.dao.TableDao
import com.pos.app.data.db.entity.MenuItemEntity
import com.pos.app.data.db.entity.OrderEntity
import com.pos.app.data.db.entity.OrderItemEntity
import com.pos.app.data.db.entity.TableEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [MenuItemEntity::class, OrderEntity::class, OrderItemEntity::class, TableEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun menuItemDao(): MenuItemDao
    abstract fun orderDao(): OrderDao
    abstract fun orderItemDao(): OrderItemDao
    abstract fun tableDao(): TableDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    seedDefaultMenu(database.menuItemDao())
                                    seedDefaultTables(database.tableDao())
                                }
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
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
