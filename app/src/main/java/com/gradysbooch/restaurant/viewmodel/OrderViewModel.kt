package com.gradysbooch.restaurant.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.lifecycle.viewModelScope
import com.gradysbooch.restaurant.model.Order
import com.gradysbooch.restaurant.model.OrderItem
import com.gradysbooch.restaurant.model.dto.AllScreenItem
import com.gradysbooch.restaurant.model.dto.Bullet
import com.gradysbooch.restaurant.model.dto.MenuItemDTO
import com.gradysbooch.restaurant.model.dto.toDTO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class OrderViewModel(application: Application) : BaseViewModel(application),
        OrderViewModelInterface
{
    init
    {
        repository.networkRepository.orderItems()
                .onEach {
                    repository.orderDao().saveOrderItems(it)
                    Log.d("OrderViewModel", "Saved order Items $it")
                }
                .launchIn(viewModelScope)

        viewModelScope.launch {
            val menuItems = repository.networkRepository.getMenuItems()
            repository.menuItemDAO().updateMenu(menuItems)
            Log.d("OrderViewModel", "Updated Menu Items: $menuItems")
        }
    }

    private val tableUID = MutableStateFlow<String?>(null)
    private val activeColor = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val clientOrderCache = repository.networkRepository.clientOrders()
            .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val table = tableUID.flatMapLatest {
        it?.let { repository.tableDao().getTable(it).filterNotNull() } ?: emptyFlow()
    }

    override val tableCode: Flow<Int?> = table.map { it.code }

    private val _allScreen = MutableStateFlow(true)
    override val allScreen: Flow<Boolean> = _allScreen

    override val bulletList: Flow<List<Bullet>> = forCurrentOrder { tableUID, activeColor ->
        val clientOrders = clientOrderCache.map { orders ->
            orders.filter { it.tableUID == tableUID }.map {
                Bullet(it.orderColor, false, it.orderColor == activeColor)
            }
        }
        val localOrders = repository.orderDao().getOrdersForTable(tableUID).map { orders ->
            orders.map {
                Bullet(it.orderColor, true, it.orderColor == activeColor)
            }
        }
        return@forCurrentOrder combine(localOrders, clientOrders) { flows ->
            (flows[0] + flows[1]).sortedBy { it.color }
        }.distinctUntilChanged { oldList: List<Bullet>, newList: List<Bullet> ->
            newList.size < oldList.size
        }
    }

    private inline fun <T> forCurrentOrder(crossinline block: (tableUID: String, color: String?) -> Flow<T>): Flow<T>
    {
        return tableUID.flatMapLatest { tableUID ->
            tableUID ?: return@flatMapLatest emptyFlow()

            activeColor.flatMapLatest colorMap@{ activeColor ->
                block(tableUID, activeColor)
            }
        }
    }

    override val requiresAttention: Flow<Boolean> = table.map { it.call }

    override val menu: Flow<List<MenuItemDTO>> = repository.menuItemDAO()
            .getMenuFlow()
            .flatMapLatest { menuItems ->
                searchQuery.mapLatest { searchCriteria ->
                    menuItems.filter { it.name.contains(searchCriteria, ignoreCase = true) }
                            .map { menuItem -> menuItem.toDTO() }
                }
            }

    override val chosenItems: Flow<List<Pair<MenuItemDTO, Int>>> =
            forCurrentOrder { tableUID, activeColor ->
                activeColor ?: return@forCurrentOrder emptyFlow()
                repository.orderDao().orderItemsWithMenuItemsFlow(tableUID, activeColor)
                        .map {orderItemWithMenuItems ->
                            orderItemWithMenuItems.map { it.menuItem.toDTO() to it.orderItem.quantity }
                        }
            }

    override val allScreenMenuItems: Flow<List<AllScreenItem>> = run {
        return@run tableUID.flatMapLatest { tableUID ->
            tableUID ?: return@flatMapLatest emptyFlow()
            repository.menuItemDAO()
                    .getMenuItemsForTable(tableUID)
                    .map { menuItemsWithOrderItems ->
                        menuItemsWithOrderItems.map {
                            val orders =
                                    it.orderItems.map { orderItem -> orderItem.orderColor to orderItem.quantity }
                            AllScreenItem(
                                    it.menuItem.toDTO(),
                                    orders.sumOf { order -> order.second },
                                    orders
                            )
                        }
                    }
        }
    }

    override val allScreenNotes: Flow<List<Pair<String, String>>> = run {
        return@run tableUID.flatMapLatest { tableUID ->
            tableUID ?: return@flatMapLatest emptyFlow()

            repository.orderDao().getOrdersForTable(tableUID).combine(clientOrderCache
            ) { localOrders, clientOrders ->
                localOrders + clientOrders.filter { it.tableUID == tableUID }
            }.map { orders -> orders.map { it.orderColor to it.note } }
        }
    }

    override suspend fun getNote(): String
    {
        val tableUID = tableUID.value ?: return ""
        val activeColor = activeColor.value ?: return ""
        return repository.orderDao().getOrder(tableUID, activeColor)?.note ?: ""
    }

    override fun setTable(tableId: String)
    {
        this.tableUID.value = tableId
        Log.d("OrderViewModel", "Changed table to id $tableId")
    }

    override fun selectAllScreen()
    {
        activeColor.value = null
        Log.d("OrderViewModel", "All screen selected")
    }

    override fun addBullet()
    {
        viewModelScope.launch {
            tableUID.value?.let { tableUID ->
                val orderColor = ColorManager.randomColor(bulletList.first().map { it.color }.toSet())
                        ?: return@let
                repository.orderDao().addOrder(
                        Order(
                                tableUID,
                                orderColor,
                                ""
                        )
                )
                repository.networkRepository.createOrder(tableUID, orderColor)
                Log.d("OrderViewModel", "Added order with color $orderColor")
            }
        }
    }

    override fun clearAttention()
    {
        viewModelScope.launch {
            tableUID.value?.let { tableUID ->
                repository.tableDao().updateTableCall(tableUID, false)
                repository.networkRepository.clearCall(tableUID)
                val notificationManager = getApplication<Application>().getSystemService<NotificationManager>() as NotificationManager
                notificationManager.cancel(tableUID.hashCode())
                Log.d("OrderViewModel", "Cleared call for table $tableUID")
            }
        }
    }

    override fun selectColor(color: String)
    {
        activeColor.value = color
        Log.d("OrderViewModel", "Selected order with color $color")
    }

    override fun search(searchString: String)
    {
        searchQuery.value = searchString
    }

    override fun changeNote(note: String)
    {
        viewModelScope.launch {
            val tableUID = tableUID.value ?: return@launch
            val activeColor = activeColor.value ?: return@launch
            repository.orderDao().updateNote(tableUID, activeColor, note)
            repository.networkRepository.updateOrder(Order(tableUID = tableUID, orderColor = activeColor, note))
        }
    }

    override fun changeNumber(menuItemId: String, number: Int)
    {
        viewModelScope.launch {
            val tableUID = tableUID.value ?: return@launch
            val activeColor = activeColor.value ?: return@launch
            repository.orderDao().changeNumber(tableUID, activeColor, menuItemId, number)

            val orderItem = OrderItem(activeColor, tableUID, menuItemId, number)
            repository.networkRepository.updateOrderItem(orderItem)
            Log.d("OrderViewModel", "Number updated for table $tableUID - $activeColor menu item $menuItemId")
        }
    }

    override fun clearTable()
    {
        val tableUID = tableUID.value ?: return
        viewModelScope.launch {
            repository.clearTable(tableUID)
            repository.networkRepository.clearTable(tableUID)
        }
        Log.d("OrderViewModel", "Cleared table")
    }

    override fun lockOrder(tableUID: String, color: Color)
    {
        viewModelScope.launch {
            val order = repository.networkRepository
                .clientOrders()
                .first()
                .find { it.tableUID == tableUID && it.orderColor == color }
                ?: return@launch
            repository.networkRepository.lockOrder(tableUID, color)
            val newOrder = if (order.note == "_") order.copy(note = "") else order
            repository.orderDao().addOrder(newOrder)
            Log.d("OrderViewModel", "Locked order $tableUID - $color")
        }
    }

    override fun unlockOrder(tableUID: String, color: Color)
    {
        viewModelScope.launch {
            if (repository.orderDao().getOrder(tableUID, color) != null)
            {
                repository.orderDao().deleteOrder(tableUID, color)
                repository.networkRepository.unlockOrder(tableUID, color)
                Log.d("OrderViewModel", "Unlocked order $tableUID - $color")
            }
        }
    }

    override fun addMenuItem(menuItemUID: String)
    {
        val orderColor = activeColor.value ?: run {
            Log.e("OrderViewModel", "No active color")
            return
        }
        val table = tableUID.value ?: run {
            Log.e("OrderViewModel", "No active table")
            return
        }
        viewModelScope.launch {
            val orderItem = OrderItem(orderColor, table, menuItemUID, 1)
            repository.orderDao().addOrderItem(orderItem)
            repository.networkRepository.createOrderItem(orderItem)
            Log.d("OrderViewModel", "Added menu item $menuItemUID")
        }
    }
}