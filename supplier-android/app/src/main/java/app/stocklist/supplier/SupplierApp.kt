package app.stocklist.supplier

import android.app.Application
import app.stocklist.supplier.core.i18n.Lang
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SupplierApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Lang.init(this)
    }
}
