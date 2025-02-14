package net.pantasystem.milktea.common_android_ui

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.databinding.BindingAdapter
import dagger.hilt.android.EntryPointAccessors
import net.pantasystem.milktea.common.glide.GlideApp
import net.pantasystem.milktea.common_android_ui.BindingProvider
import net.pantasystem.milktea.model.notes.reaction.LegacyReaction

object ReactionViewHelper {
    @BindingAdapter("reactionImageView", "reactionStringView", "reaction")
    @JvmStatic
    fun LinearLayout.setReaction(
        reactionImageView: ImageView,
        reactionStringView: TextView,
        reaction: String
    ) {
        setReaction(this.context, reactionImageView, reactionStringView, reaction)

    }

    @BindingAdapter("reactionImageView", "reactionStringView", "reaction")
    @JvmStatic
    fun FrameLayout.setReaction(
        reactionImageView: ImageView,
        reactionStringView: TextView,
        reaction: String
    ) {
        setReaction(this.context, reactionImageView, reactionStringView, reaction)
    }

    /*private var emojiHandler: Handler? = null
    private val emojiThread = Thread{
        Looper.prepare()
        emojiHandler = Handler()

        Looper.loop()
    }.run()*/

    private fun setReaction(
        context: Context,
        reactionImageView: ImageView,
        reactionStringView: TextView,
        reaction: String
    ) {

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BindingProvider::class.java
        )
        val cache = entryPoint.metaCache()
        val accountStore = entryPoint.accountStore()


        //Log.d("ReactionViewHelper", "reaction $reaction")
        if (reaction.startsWith(":") && reaction.endsWith(":")) {
            val account = accountStore.currentAccount
            val emojis = if (account?.instanceDomain != null) {
                cache.get(account.instanceDomain)?.emojis ?: emptyList()
            } else {
                emptyList()
            }
            val emoji = emojis.firstOrNull {
                it.name == reaction.replace(":", "")
            }

            if (emoji != null) {
                //Log.d("ReactionViewHelper", "カスタム絵文字を発見した: ${emoji}")
                GlideApp.with(reactionImageView.context)
                    .load(emoji.url ?: emoji.uri)
                    .centerCrop()
                    .into(reactionImageView)
                reactionImageView.visibility = View.VISIBLE
                reactionStringView.visibility = View.GONE
                return
            } else {
                Log.d("ReactionViewHelper", "emoji not found")
                reactionImageView.visibility = View.GONE
                reactionStringView.visibility = View.GONE
            }

        }

        val constantReaction = LegacyReaction.reactionMap[reaction]
        if (constantReaction != null) {

            reactionStringView.text = constantReaction
            reactionImageView.visibility = View.GONE
            reactionStringView.visibility = View.VISIBLE
        } else {
            //Log.d("ReactionViewHelper", "どれにも当てはまらなかった")
            reactionStringView.text = reaction
            reactionImageView.visibility = View.GONE
            reactionStringView.visibility = View.VISIBLE
        }

    }



}