package com.schoolbus.base

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.schoolbus.R
import com.yanzhenjie.loading.dialog.LoadingDialog

open class CoreFragment: Fragment() {
    var progress: LoadingDialog? = null

    open fun openActivity(destinationClass: Class<*>?) {
        startActivity(Intent(activity, destinationClass))
    }

    open fun hideProgress() {
        if (progress != null) {
            progress?.dismiss()
            progress = null
        }
    }

    open fun showProgress() {
        hideProgress()
        progress = LoadingDialog(requireContext())
        progress?.setCircleColors(
            ContextCompat.getColor(requireContext(), R.color.colorText),
            ContextCompat.getColor(requireContext(), R.color.colorPrimary),
            ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark)
        )
        progress?.show()
    }
}