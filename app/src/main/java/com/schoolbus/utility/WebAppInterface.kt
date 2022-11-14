package com.schoolbus.utility

import android.content.Context
import android.webkit.JavascriptInterface

class WebAppInterface(
    private val mContext: Context,
    val onGetStop: ((String) -> Unit)? = null,
    val onHideTomTom: ((String) -> Unit)? = null,
    val onShowTomTom: ((String) -> Unit)? = null
) {
//class WebAppInterface(private val mContext: Context, val onGetStop: ((String) -> Unit)? = null,val onShowTomTom:((String) -> Unit),val onHideTomTom:((String) -> Unit)){
//    class WebAppInterface(private val mContext: Context) {


    /** Show a toast from the web page  */

    @JavascriptInterface
    fun loadStops(_busStopGeoJson: String) {
        onGetStop?.invoke(_busStopGeoJson)
        println("owebappinterface 12" + _busStopGeoJson)
    }

    @JavascriptInterface
    fun showTomTom() {
        println("we need to display tomtom now")
        onShowTomTom?.invoke("temp")
    }

    @JavascriptInterface
    fun hideTomTom() {
        println("we need to hide tomtom now")
        onHideTomTom?.invoke("temp")
    }

}