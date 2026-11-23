package vn.uit.healthos.feature.safety.card

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat

/** Quick Settings tile that opens the lock-screen Emergency Health Card. */
class EmergencyCardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, EmergencyCardActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val wrapper = PendingIntentActivityWrapper(
            this,
            7010,
            intent,
            0,
            false,
        )
        TileServiceCompat.startActivityAndCollapse(this, wrapper)
    }
}
