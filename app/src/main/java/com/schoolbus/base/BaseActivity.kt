package com.schoolbus.base

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import com.schoolbus.R
import com.yanzhenjie.loading.dialog.LoadingDialog

/**
 * Created by Mr.PM.. on 31/03/21.
 */

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    lateinit var binding: VB
    var progress: LoadingDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = setBinding(layoutInflater)
        setContentView(binding.root)
        InitView()
    }

    protected abstract fun setBinding(layoutInflater: LayoutInflater): VB

    protected abstract fun InitView()

    open fun openActivity(destinationClass: Class<*>) {
        startActivity(Intent(this@BaseActivity, destinationClass))
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        //overridePendingTransition(R.anim.fad_in, R.anim.fad_out)
    }

    open fun hideProgress() {
        if (progress != null) {
            progress?.dismiss()
            progress = null
        }
    }

    open fun showProgress() {
        hideProgress()
        progress = LoadingDialog(this)
        progress?.setCircleColors(
            ContextCompat.getColor(this, R.color.colorText),
            ContextCompat.getColor(this, R.color.colorPrimary),
            ContextCompat.getColor(this, R.color.colorPrimaryDark)
        )
        progress?.show()
    }
}