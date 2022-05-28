package com.example.testapp.components.channel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.testapp.R
import com.example.testapp.databinding.TinuiFragmentContainerBinding

/**
 * An Activity representing a self-contained channel list screen. This Activity
 * is simply a thin wrapper around [ChannelListFragment].
 */
public open class ChannelListActivity : AppCompatActivity() {
    private lateinit var binding: TinuiFragmentContainerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = TinuiFragmentContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, createChannelListFragment())
                .commit()
        }
    }

    /**
     * Creates an instance of [ChannelListFragment]. Override this method if you want to create an
     * instance of [ChannelListFragment] with custom arguments or if you want to create a subclass
     * of [ChannelListFragment].
     */
    protected open fun createChannelListFragment(): ChannelListFragment {
        return ChannelListFragment.newInstance {
            setFragment(ChannelListFragment())
            customTheme(R.style.tinuiTheme_ChannelListScreen)
            showSearch(true)
            showHeader(true)
            headerTitle(getString(R.string.tinui_channel_list_header_connected))
        }
    }

    public companion object {
        public fun createIntent(context: Context): Intent {
            return Intent(context, ChannelListActivity::class.java)
        }
    }
}
