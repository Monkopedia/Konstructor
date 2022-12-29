package com.monkopedia.konstructor.logging

import com.monkopedia.hauler.DeliveryService
import com.monkopedia.hauler.DropBox
import com.monkopedia.hauler.LoadingDock
import com.monkopedia.hauler.Shipper
import com.monkopedia.hauler.Warehouse

class WarehouseWrapper private constructor(private val warehouse: Warehouse = Warehouse()) :
    Shipper {

    fun getScoped(tagPrefix: String, name: String): Shipper {
        return ScopedShipper(tagPrefix, name, warehouse)
    }

    override suspend fun deliveries(u: Unit): DeliveryService {
        return warehouse.deliveries()
    }

    override suspend fun requestDockPickup(u: Unit): LoadingDock {
        return warehouse.requestDockPickup()
    }

    override suspend fun requestPickup(u: Unit): DropBox {
        return warehouse.requestPickup()
    }

    companion object : () -> WarehouseWrapper {
        private var warehouse: WarehouseWrapper? = null
        override fun invoke(): WarehouseWrapper {
            return warehouse ?: WarehouseWrapper().also { warehouse = it }
        }
    }
}
