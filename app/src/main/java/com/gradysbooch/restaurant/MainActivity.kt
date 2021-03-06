package com.gradysbooch.restaurant

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.viewinterop.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import com.gradysbooch.restaurant.notifications.NotificationReceiver
import com.gradysbooch.restaurant.ui.screens.orders.OrderScreen
import com.gradysbooch.restaurant.ui.screens.tables.TablesScreen
import com.gradysbooch.restaurant.ui.values.RestaurantmobileTheme
import com.gradysbooch.restaurant.viewmodel.OrderViewModel
import com.gradysbooch.restaurant.viewmodel.TableViewModel

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

        val extras = intent.extras

        setContent {
            extras?.getString("tableUID")?.let {
                App(startLocation = "orders/$it")
            } ?: App()

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

// @Preview
@Composable
fun App(tableViewModel: TableViewModel = viewModel<TableViewModel>(),
        orderViewModel: OrderViewModel = viewModel<OrderViewModel>(),
        startLocation: String = "tables") {

    var newStartLocation by mutableStateOf(startLocation)
    RestaurantmobileTheme {
        Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background
        ) {
            val screenNavController = rememberNavController()

            NavHost(navController = screenNavController, startDestination = "tables") {

                composable("tables"){
                    if (newStartLocation != "tables")
                    {
                        screenNavController.navigate(startLocation)
                        newStartLocation = "tables"
                    }
                    else
                        TablesScreen(tableViewModel, orderViewModel, screenNavController).Show()
                }

                composable("orders/{tableId}",
                        arguments = listOf(
                                navArgument("tableId") { type = NavType.StringType },
                        )
                ) {
                    val tableId = it.arguments?.getString("tableId")!!
                    OrderScreen(orderViewModel, screenNavController,
                                tableId,
                    ).Show()
                }
            }
        }
    }
}

