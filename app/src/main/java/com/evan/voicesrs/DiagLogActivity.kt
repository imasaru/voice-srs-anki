package com.evan.voicesrs

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/** h89: in-app diagnostic log viewer shell (list/filter filled in a later round). */
class DiagLogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diag_log)
        findViewById<MaterialToolbar>(R.id.diagLogToolbar).setNavigationOnClickListener { finish() }
    }
}
