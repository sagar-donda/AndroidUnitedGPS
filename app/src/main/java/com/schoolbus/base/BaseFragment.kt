package com.schoolbus.base

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.schoolbus.R
import com.yanzhenjie.loading.dialog.LoadingDialog

/**
 * Created by Mr.PM.. on 31/03/21.
 */

abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    lateinit var binding: VB
    var progress: LoadingDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = getViewBinding(inflater, container)
        return binding.root
    }

    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Init(view)
    }

    protected abstract fun Init(view: View)

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