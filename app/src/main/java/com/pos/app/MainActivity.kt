package com.pos.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pos.app.data.db.AppDatabase
import com.pos.app.ui.navigation.NavGraph
import com.pos.app.ui.theme.PosAndroidTheme
import com.pos.app.util.AutoBackupManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appDatabase: AppDatabase
    @Inject lateinit var autoBackupManager: AutoBackupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PosAndroidTheme {
                NavGraph()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 進入前景時就先排程一次閒置備份，即使使用者之後沒操作也會在閒置時間後觸發
        autoBackupManager.onUserActivity()
    }

    /**
     * App 進入背景時：
     * 1) 把尚未落地的交易 checkpoint 到主 DB 檔（崩潰保險）。
     * 2) 同時觸發一次自動備份，避免「切出 App 後被系統殺掉」導致備份從未執行。
     */
    override fun onPause() {
        super.onPause()
        runCatching {
            appDatabase.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .close()
        }
        autoBackupManager.onAppBackgrounded()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        autoBackupManager.onUserActivity()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        autoBackupManager.onUserActivity()
        return super.dispatchKeyEvent(event)
    }
}
