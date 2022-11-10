package com.schoolbus.fragment

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import com.schoolbus.BuildConfig
import com.schoolbus.R
import com.schoolbus.base.BaseFragment
import com.schoolbus.databinding.FragmentMainBinding
import com.schoolbus.utility.NetworkHelper
import com.schoolbus.utility.gone
import com.schoolbus.utility.isVisible
import com.schoolbus.utility.visible

class MainFragment : BaseFragment<FragmentMainBinding>() {

    private var mLoaded = false

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentMainBinding = FragmentMainBinding.inflate(inflater,container,false)

    override fun Init(view: View) {
        /**
         * request for show website
         */
        requestForWebView()
        initClick()
    }

    private fun initClick() {
        binding.btnTryAgain.setOnClickListener {
            binding.webview.gone()
            binding.progressBar.visible()
            binding.layoutSplash.visible()
            binding.layoutNoInternet.gone()
            requestForWebView()
        }
    }

    private fun requestForWebView() {
        if (!mLoaded) {
            requestWebView()
            Handler(Looper.getMainLooper()).postDelayed({
                binding.progressBar.visible()
                binding.webview.visible()
            }, 3000)

        } else {
            binding.webview.visible()
            binding.progressBar.gone()
            binding.layoutSplash.gone()
            binding.layoutNoInternet.gone()
        }

    }

    @SuppressLint("SetJavaScriptEnabled")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun requestWebView() {
        /**
         * Layout of WebView screen View
         */
        if (NetworkHelper(requireContext()).isNetworkConnected()) {
            binding.webview.visible()
            binding.layoutNoInternet.gone()
            binding.webview.loadUrl(BuildConfig.WEB_URL)
        } else {
            binding.progressBar.gone()
            binding.webview.gone()
            binding.layoutSplash.gone()
            binding.layoutNoInternet.visible()
            return
        }

        binding.webview.isFocusable = true
        binding.webview.isFocusableInTouchMode = true
        binding.webview.settings.javaScriptEnabled = true
        binding.webview.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        binding.webview.settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
        binding.webview.settings.cacheMode = WebSettings.LOAD_DEFAULT
        binding.webview.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        binding.webview.settings.domStorageEnabled = true
        binding.webview.settings.setAppCacheEnabled(true)
        binding.webview.settings.databaseEnabled = true

        /**
         * this force use ChromeWebClient
         */
        binding.webview.settings.setSupportMultipleWindows(false)
        binding.webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {

                Log.d("TAG", "URL: " + url!!)
                if (NetworkHelper(requireContext()).isNetworkConnected()) {
                    view.loadUrl(url)
                } else {
                    binding.progressBar.gone()
                    binding.webview.gone()
                    binding.layoutSplash.gone()
                    binding.layoutNoInternet.visible()
                }

                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!binding.progressBar.isVisible()) {
                    binding.progressBar.visible()
                }
            }

            override fun onLoadResource(view: WebView, url: String) {
                super.onLoadResource(view, url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                mLoaded = true
                if (binding.progressBar.isVisible())
                    binding.progressBar.gone()

                // check if layoutSplash is still there, get it away!
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.layoutSplash.gone()
                }, 2000)
            }
        }
    }
}