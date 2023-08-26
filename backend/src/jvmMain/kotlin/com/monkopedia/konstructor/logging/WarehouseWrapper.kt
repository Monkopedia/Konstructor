/*
 * Copyright 2022 Jason Monk
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
