package com.gradysbooch.restaurant

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.viewinterop.viewModel
import androidx.ui.tooling.preview.Preview
import com.gradysbooch.restaurant.model.Table
import com.gradysbooch.restaurant.notifications.NotificationReceiver
import com.gradysbooch.restaurant.ui.screens.order.OrderScreen
import com.gradysbooch.restaurant.ui.screens.tables.TablesScreen
import com.gradysbooch.restaurant.ui.values.RestaurantmobileTheme
import com.gradysbooch.restaurant.viewmodel.OrderViewModel

class MainActivity : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        NotificationReceiver.createNotificationChannel(
                getString(R.string.channel_id),
                getString(R.string.channel_name),
                getString(R.string.channel_description),
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

        setContent {
            App()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationReceiver.removeNotificationChannel(
                    getString(R.string.channel_id),
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        }
    }
}

@Preview
@Composable
fun App() {
    RestaurantmobileTheme {
        // A surface container using the 'background' color from the theme
        Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background
        ) {
            /**
             *  The Selected Table ID will control what screen is displayed
             *      main -> TablesScreen
             *      anything else -> OrderScreen
             * todo error -> ErrorScreen
             */
            val orderViewModel = viewModel<OrderViewModel>()
            val selectedTable by orderViewModel.table
                    .collectAsState(initial = Table("-1", "name", 0, false))
            if (selectedTable.code != 0) TablesScreen()
            else OrderScreen()
        }
    }
}

