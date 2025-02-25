package com.kickstarter.ui.activities

import android.os.Bundle
import android.util.Pair
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.kickstarter.R
import com.kickstarter.databinding.ActivityThreadLayoutBinding
import com.kickstarter.libs.BaseActivity
import com.kickstarter.libs.KSString
import com.kickstarter.libs.RecyclerViewPaginator
import com.kickstarter.libs.qualifiers.RequiresActivityViewModel
import com.kickstarter.libs.utils.ApplicationUtils
import com.kickstarter.libs.utils.UrlUtils
import com.kickstarter.libs.utils.extensions.showAlertDialog
import com.kickstarter.models.Comment
import com.kickstarter.ui.adapters.RepliesAdapter
import com.kickstarter.ui.adapters.RepliesStatusAdapter
import com.kickstarter.ui.adapters.RootCommentAdapter
import com.kickstarter.ui.extensions.hideKeyboard
import com.kickstarter.ui.views.OnCommentComposerViewClickedListener
import com.kickstarter.viewmodels.ThreadViewModel
import org.joda.time.DateTime
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

@RequiresActivityViewModel(ThreadViewModel.ViewModel::class)
class ThreadActivity :
    BaseActivity<ThreadViewModel.ViewModel>(),
    RepliesStatusAdapter.Delegate,
    RepliesAdapter.Delegate {

    private lateinit var binding: ActivityThreadLayoutBinding
    private lateinit var ksString: KSString

    /** Replies list adapter **/
    private val repliesAdapter = RepliesAdapter(this)
    /**  Replies cell status viewMore or Error  **/
    private val repliesStatusAdapter = RepliesStatusAdapter(this)
    /** Replies Root comment cell adapter **/
    private val rootCommentAdapter = RootCommentAdapter()
    /** reverse Layout to bind the replies from bottom **/
    private val linearLayoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, true)

    private lateinit var recyclerViewPaginator: RecyclerViewPaginator
    var isPaginated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityThreadLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ksString = requireNotNull(environment().ksString())
        recyclerViewPaginator = RecyclerViewPaginator(binding.commentRepliesRecyclerView, { viewModel.inputs.nextPage() }, viewModel.outputs.isFetchingReplies(), false)

        /** use ConcatAdapter to bind adapters to recycler view and replace the section issue **/
        binding.commentRepliesRecyclerView.adapter = ConcatAdapter(repliesAdapter, repliesStatusAdapter, rootCommentAdapter)
        binding.commentRepliesRecyclerView.layoutManager = linearLayoutManager

        this.viewModel.outputs.getRootComment()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { comment ->
                /** bind root comment by updating the adapter list**/
                rootCommentAdapter.updateRootCommentCell(comment)
            }

        this.viewModel.outputs
            .onCommentReplies()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                linearLayoutManager.stackFromEnd = false
            }
            .subscribe {
                /** bind View more cell if the replies more than 7 or update after refresh initial error state **/
                this.repliesStatusAdapter.addViewMoreCell(it.second)
                if (it.first.isNotEmpty()) {
                    this.repliesAdapter.takeData(it.first)
                }
            }

        viewModel.outputs.shouldShowPaginationErrorUI()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .filter { it }
            .subscribe {
                /** bind Error Pagination cell **/
                repliesStatusAdapter.addErrorPaginationCell(it)
            }

        viewModel.outputs.initialLoadCommentsError()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .filter { it }
            .subscribe {
                /** bind Error initial loading cell **/
                repliesStatusAdapter.addInitiallyLoadingErrorCell(it)
            }

        viewModel.outputs.isFetchingReplies()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                binding.repliesLoadingIndicator.isVisible = it
            }

        this.viewModel.shouldFocusOnCompose()
            .delay(30, TimeUnit.MILLISECONDS)
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { shouldOpenKeyboard ->
                binding.replyComposer.requestCommentComposerKeyBoard(shouldOpenKeyboard)
            }

        viewModel.outputs.currentUserAvatar()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                binding.replyComposer.setAvatarUrl(it)
            }

        viewModel.outputs.replyComposerStatus()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                binding.replyComposer.setCommentComposerStatus(it)
            }

        viewModel.outputs.scrollToBottom()
            .compose(bindToLifecycle())
            .delay(500, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { binding.commentRepliesRecyclerView.smoothScrollToPosition(0) }

        viewModel.outputs.showReplyComposer()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                binding.replyComposer.isVisible = it
            }

        viewModel.outputs.loadMoreReplies()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                recyclerViewPaginator.reload()
            }

        binding.replyComposer.setCommentComposerActionClickListener(object :
                OnCommentComposerViewClickedListener {
                override fun onClickActionListener(string: String) {
                    postReply(string)
                    hideKeyboard()
                }
            })

        viewModel.outputs.showCommentGuideLinesLink()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                ApplicationUtils.openUrlExternally(
                    this,
                    UrlUtils.appendPath(
                        environment().webEndpoint(),
                        CommentsActivity.COMMENT_KICKSTARTER_GUIDELINES
                    )
                )
            }

        viewModel.outputs.hasPendingComments()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                if (it) handleBackAction() else viewModel.inputs.backPressed()
            }

        viewModel.outputs.closeThreadActivity()
            .compose(bindToLifecycle())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                closeCommentsActivity()
            }
    }

    private fun handleBackAction() {
        this.showAlertDialog(
            getString(R.string.Your_comment_wasnt_posted),
            getString(R.string.You_will_lose_the_comment_if_you_leave_this_page),
            getString(R.string.Cancel),
            getString(R.string.Leave_page),
            false,
            positiveAction = {
            },
            negativeAction = {
                viewModel.inputs.backPressed()
            }
        )
    }

    override fun back() {
        if (binding.replyComposer.isCommentComposerEmpty() == true) {
            viewModel.inputs.checkIfThereAnyPendingComments()
        } else {
            handleBackAction()
        }
    }

    private fun closeCommentsActivity() {
        super.back()
        this.finishActivity(taskId)
    }

    fun postReply(comment: String) {
        this.viewModel.inputs.insertNewReplyToList(comment, DateTime.now())
        this.binding.replyComposer.clearCommentComposer()
    }

    override fun onStop() {
        super.onStop()
        hideKeyboard()
    }

    override fun exitTransition(): Pair<Int, Int>? {
        return Pair.create(R.anim.fade_in_slide_in_left, R.anim.slide_out_right)
    }

    override fun onRetryViewClicked(comment: Comment) {
    }

    override fun onReplyButtonClicked(comment: Comment) {
    }

    override fun onFlagButtonClicked(comment: Comment) {
        TODO("Not yet implemented")
    }

    override fun onCommentGuideLinesClicked(comment: Comment) {
        viewModel.inputs.onShowGuideLinesLinkClicked()
    }

    override fun onCommentRepliesClicked(comment: Comment) {
    }

    override fun onCommentPostedFailed(comment: Comment, position: Int) {
        viewModel.inputs.refreshCommentCardInCaseFailedPosted(comment, position)
    }

    override fun onCommentPostedSuccessFully(comment: Comment, position: Int) {
        viewModel.inputs.refreshCommentCardInCaseSuccessPosted(comment, position)
    }

    override fun loadMoreCallback() {
        viewModel.inputs.reloadRepliesPage()
    }

    override fun retryCallback() {
        viewModel.inputs.reloadRepliesPage()
    }

    override fun onShowCommentClicked(comment: Comment) {
        viewModel.inputs.onShowCanceledPledgeComment(comment)
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerViewPaginator.stop()
        binding.commentRepliesRecyclerView.adapter = null
        this.viewModel = null
    }
}
