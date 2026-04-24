package com.pos.app.di

import android.content.Context
import com.pos.app.data.datastore.SettingsDataStore
import com.pos.app.data.db.AppDatabase
import com.pos.app.data.db.dao.MenuGroupDao
import com.pos.app.data.db.dao.MenuItemDao
import com.pos.app.data.db.dao.OrderDao
import com.pos.app.data.db.dao.OrderItemDao
import com.pos.app.data.db.dao.ReservationDao
import com.pos.app.data.db.dao.TableDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        AppDatabase.getInstance(ctx)

    @Provides @Singleton
    fun provideMenuGroupDao(db: AppDatabase): MenuGroupDao = db.menuGroupDao()

    @Provides @Singleton
    fun provideMenuItemDao(db: AppDatabase): MenuItemDao = db.menuItemDao()

    @Provides @Singleton
    fun provideOrderDao(db: AppDatabase): OrderDao = db.orderDao()

    @Provides @Singleton
    fun provideOrderItemDao(db: AppDatabase): OrderItemDao = db.orderItemDao()

    @Provides @Singleton
    fun provideTableDao(db: AppDatabase): TableDao = db.tableDao()

    @Provides @Singleton
    fun provideReservationDao(db: AppDatabase): ReservationDao = db.reservationDao()

    @Provides @Singleton
    fun provideSettingsDataStore(@ApplicationContext ctx: Context): SettingsDataStore =
        SettingsDataStore(ctx)
}
