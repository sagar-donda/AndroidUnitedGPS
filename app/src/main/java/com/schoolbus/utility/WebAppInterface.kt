package com.schoolbus.utility

import android.content.Context
import android.webkit.JavascriptInterface

class WebAppInterface(
    private val mContext: Context,
    val onGetStop: ((String) -> Unit)? = null,
    val hideTomTom: (() -> Unit)? = null,
    val showTomTom: (() -> Unit)? = null
) {
//class WebAppInterface(private val mContext: Context, val onGetStop: ((String) -> Unit)? = null,val onShowTomTom:((String) -> Unit),val onHideTomTom:((String) -> Unit)){
//    class WebAppInterface(private val mContext: Context) {


    /** Show a toast from the web page  */

    @JavascriptInterface
    fun loadStops(_busStopGeoJson: String) {
        onGetStop?.invoke(_busStopGeoJson)
        println("owebappinterface 12" + _busStopGeoJson)
    }

    /** Show a toast from the web page  */
    @JavascriptInterface
    fun hideTomTom() {
        hideTomTom?.invoke()
    }

    /** Show a toast from the web page  */
    @JavascriptInterface
    fun showTomTom() {
        showTomTom?.invoke()
    }

}