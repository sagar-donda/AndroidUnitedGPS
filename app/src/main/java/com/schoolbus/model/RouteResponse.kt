package com.schoolbus.model

import com.google.gson.annotations.SerializedName

data class RouteResponse(

	@field:SerializedName("RouteResponse")
	val routeResponse: ArrayList<RouteResponseItem> = arrayListOf()
)

data class RouteResponseItem(

	@field:SerializedName("geometry")
	val geometry: Geometry? = null,

	@field:SerializedName("type")
	val type: String? = null,

	@field:SerializedName("properties")
	val properties: Properties? = null
)

data class Geometry(

	@field:SerializedName("coordinates")
	val coordinates: ArrayList<String> = arrayListOf(),

	@field:SerializedName("type")
	val type: String? = null
)

data class Properties(

	@field:SerializedName("address")
	val address: String? = null,

	@field:SerializedName("stop_id")
	val stopId: String? = null,

	@field:SerializedName("stop_type")
	val stopType: String? = null
)
