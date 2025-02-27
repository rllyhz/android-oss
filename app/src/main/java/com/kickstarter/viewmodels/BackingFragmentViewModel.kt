package com.kickstarter.viewmodels

import android.util.Pair
import androidx.annotation.NonNull
import com.kickstarter.R
import com.kickstarter.libs.Either
import com.kickstarter.libs.Environment
import com.kickstarter.libs.FragmentViewModel
import com.kickstarter.libs.KSString
import com.kickstarter.libs.rx.transformers.Transformers.combineLatestPair
import com.kickstarter.libs.rx.transformers.Transformers.neverError
import com.kickstarter.libs.rx.transformers.Transformers.takePairWhen
import com.kickstarter.libs.utils.DateTimeUtils
import com.kickstarter.libs.utils.NumberUtils
import com.kickstarter.libs.utils.ObjectUtils
import com.kickstarter.libs.utils.ProjectViewUtils
import com.kickstarter.libs.utils.RewardUtils
import com.kickstarter.libs.utils.extensions.backedReward
import com.kickstarter.libs.utils.extensions.isErrored
import com.kickstarter.libs.utils.extensions.negate
import com.kickstarter.libs.utils.extensions.userIsCreator
import com.kickstarter.mock.factories.RewardFactory
import com.kickstarter.models.Backing
import com.kickstarter.models.PaymentSource
import com.kickstarter.models.Project
import com.kickstarter.models.Reward
import com.kickstarter.models.StoredCard
import com.kickstarter.models.User
import com.kickstarter.models.extensions.getCardTypeDrawable
import com.kickstarter.ui.data.PledgeStatusData
import com.kickstarter.ui.data.ProjectData
import com.kickstarter.ui.fragments.BackingFragment
import com.stripe.android.model.Card
import com.stripe.android.model.CardBrand
import org.joda.time.DateTime
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import type.CreditCardPaymentType
import type.CreditCardTypes
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

interface BackingFragmentViewModel {
    interface Inputs {
        /** Configure with current [ProjectData]. */
        fun configureWith(projectData: ProjectData)

        /** Call when the fix payment method button is clicked. */
        fun fixPaymentMethodButtonClicked()

        /** Call when the pledge has been successfully updated. */
        fun pledgeSuccessfullyUpdated()

        /** Call when the mark as received checkbox is checked. */
        fun receivedCheckboxToggled(checked: Boolean)

        /** Call when the swipe refresh layout is triggered. */
        fun refreshProject()
    }

    interface Outputs {
        /** Emits the backer's avatar URL. */
        fun backerAvatar(): Observable<String>

        /** Emits the backer's name. */
        fun backerName(): Observable<String>

        /** Emits the backer's sequence. */
        fun backerNumber(): Observable<String>

        /** Emits the expiration of the backing's card. */
        fun cardExpiration(): Observable<String>

        /** Emits the name of the card issuer from [Card.CardBrand] or Google Pay or Apple Pay string resources. */
        fun cardIssuer(): Observable<Either<String, Int>>

        /** Emits the last four digits of the backing's card. */
        fun cardLastFour(): Observable<String>

        /** Emits the card brand drawable to display. */
        fun cardLogo(): Observable<Int>

        /** Emits a boolean determining if the fix payment method button should be visible. */
        fun fixPaymentMethodButtonIsGone(): Observable<Boolean>

        /** Emits a boolean determining if the fix payment method message should be visible. */
        fun fixPaymentMethodMessageIsGone(): Observable<Boolean>

        /** Emits when we should notify the [BackingFragment.BackingDelegate] to refresh the project. */
        fun notifyDelegateToRefreshProject(): Observable<Void>

        /** Call when the [BackingFragment.BackingDelegate] should be notified to show the fix pledge flow. */
        fun notifyDelegateToShowFixPledge(): Observable<Void>

        /** Emits a boolean determining if the payment method section should be visible. */
        fun paymentMethodIsGone(): Observable<Boolean>

        /** Emits the amount pledged minus the shipping. */
        fun pledgeAmount(): Observable<CharSequence>

        /** Emits the date the backing was pledged on. */
        fun pledgeDate(): Observable<String>

        /** Emits the string resource ID that best represents the pledge status and associated data. */
        fun pledgeStatusData(): Observable<PledgeStatusData>

        /** Emits a boolean determining if the pledge summary should be visible. */
        fun pledgeSummaryIsGone(): Observable<Boolean>

        /** Emits the [ProjectData] and currently backed [Reward]. */
        fun projectDataAndReward(): Observable<Pair<ProjectData, Reward>>

        /** Emits the [ProjectData] and currently selected AddOns: [List<Reward>]. */
        fun projectDataAndAddOns(): Observable<Pair<ProjectData, List<Reward>>>

        /** Emits a boolean that determines if received checkbox should be checked. */
        fun receivedCheckboxChecked(): Observable<Boolean>

        /** Emits a boolean determining if the delivered section should be visible for the backer perspective. */
        fun receivedSectionIsGone(): Observable<Boolean>

        /** Emits a boolean determining if the delivered section should be visible for the creator perspective. */
        fun receivedSectionCreatorIsGone(): Observable<Boolean>

        /** Emits the shipping amount of the backing. */
        fun shippingAmount(): Observable<CharSequence>

        /** Emits the shipping location of the backing. */
        fun shippingLocation(): Observable<String>

        /** Emits a boolean determining if the shipping summary should be visible. */
        fun shippingSummaryIsGone(): Observable<Boolean>

        /** Emits when the backing has successfully been updated. */
        fun showUpdatePledgeSuccess(): Observable<Void>

        /** Emits a boolean determining if the swipe refresher is visible. */
        fun swipeRefresherProgressIsVisible(): Observable<Boolean>

        /** Emits the total amount pledged. */
        fun totalAmount(): Observable<CharSequence>

        /** Emits the bonus support added to the pledge, if any **/
        fun bonusSupport(): Observable<CharSequence>

        /** Emits the estimated delivery date of this reward **/
        fun estimatedDelivery(): Observable<String>

        /** Emits a boolean determining if the delivery disclaimer section is visible **/
        fun deliveryDisclaimerSectionIsGone(): Observable<Boolean>
    }

    class ViewModel(@NonNull val environment: Environment) : FragmentViewModel<BackingFragment>(environment), Inputs, Outputs {

        private val fixPaymentMethodButtonClicked = PublishSubject.create<Void>()
        private val pledgeSuccessfullyCancelled = PublishSubject.create<Void>()
        private val projectDataInput = PublishSubject.create<ProjectData>()
        private val receivedCheckboxToggled = PublishSubject.create<Boolean>()
        private val refreshProject = PublishSubject.create<Void>()

        private val backerAvatar = BehaviorSubject.create<String>()
        private val backerName = BehaviorSubject.create<String>()
        private val backerNumber = BehaviorSubject.create<String>()
        private val cardExpiration = BehaviorSubject.create<String>()
        private val cardIssuer = BehaviorSubject.create<Either<String, Int>>()
        private val cardLastFour = BehaviorSubject.create<String>()
        private val cardLogo = BehaviorSubject.create<Int>()
        private val fixPaymentMethodButtonIsGone = BehaviorSubject.create<Boolean>()
        private val fixPaymentMethodMessageIsGone = BehaviorSubject.create<Boolean>()
        private val notifyDelegateToRefreshProject = PublishSubject.create<Void>()
        private val notifyDelegateToShowFixPledge = PublishSubject.create<Void>()
        private val paymentMethodIsGone = BehaviorSubject.create<Boolean>()
        private val pledgeAmount = BehaviorSubject.create<CharSequence>()
        private val pledgeDate = BehaviorSubject.create<String>()
        private val pledgeStatusData = BehaviorSubject.create<PledgeStatusData>()
        private val pledgeSummaryIsGone = BehaviorSubject.create<Boolean>()
        private val projectDataAndReward = BehaviorSubject.create<Pair<ProjectData, Reward>>()
        private val receivedCheckboxChecked = BehaviorSubject.create<Boolean>()
        private val receivedSectionIsGone = BehaviorSubject.create<Boolean>()
        private val receivedSectionCreatorIsGone = BehaviorSubject.create<Boolean>()
        private val shippingAmount = BehaviorSubject.create<CharSequence>()
        private val shippingLocation = BehaviorSubject.create<String>()
        private val shippingSummaryIsGone = BehaviorSubject.create<Boolean>()
        private val showUpdatePledgeSuccess = PublishSubject.create<Void>()
        private val swipeRefresherProgressIsVisible = BehaviorSubject.create<Boolean>()
        private val totalAmount = BehaviorSubject.create<CharSequence>()
        private val addOnsList = BehaviorSubject.create<Pair<ProjectData, List<Reward>>>()
        private val bonusSupport = BehaviorSubject.create<CharSequence>()
        private val estimatedDelivery = BehaviorSubject.create<String>()
        private val deliveryDisclaimerSectionIsGone = BehaviorSubject.create<Boolean>()

        private val apiClient = requireNotNull(this.environment.apiClient())
        private val apolloClient = requireNotNull(this.environment.apolloClient())
        private val ksCurrency = requireNotNull(this.environment.ksCurrency())
        val ksString: KSString? = this.environment.ksString()
        private val currentUser = requireNotNull(this.environment.currentUser())

        val inputs: Inputs = this
        val outputs: Outputs = this

        init {

            this.pledgeSuccessfullyCancelled
                .compose(bindToLifecycle())
                .subscribe(this.showUpdatePledgeSuccess)

            this.projectDataInput
                .filter { it.project().isBacking() || it.project().userIsCreator(it.user()) }
                .map { projectData -> joinProjectDataAndReward(projectData) }
                .compose(bindToLifecycle())
                .subscribe(this.projectDataAndReward)

            val backedProject = this.projectDataInput
                .map { it.project() }

            val backing = this.projectDataInput
                .switchMap { getBackingInfo(it) }
                .compose(neverError())
                .filter { ObjectUtils.isNotNull(it) }
                .share()

            val rewardA = backing
                .filter { ObjectUtils.isNotNull(it.reward()) }
                .map { requireNotNull(it.reward()) }

            val rewardB = projectDataAndReward
                .filter { ObjectUtils.isNotNull(it.second) }
                .map { requireNotNull(it.second) }

            val reward = Observable.merge(rewardA, rewardB)
                .distinctUntilChanged()

            val isCreator = Observable.combineLatest(this.currentUser.observable(), backedProject) { user, project ->
                Pair(user, project)
            }
                .map { it.second.userIsCreator(it.first) }

            backing
                .map { it.backerName() }
                .filter { ObjectUtils.isNotNull(it) }
                .compose(bindToLifecycle())
                .subscribe(this.backerName)

            backing
                .map { it.backerUrl() }
                .filter { ObjectUtils.isNotNull(it) }
                .compose(bindToLifecycle())
                .subscribe(this.backerAvatar)

            backing
                .map { NumberUtils.format(it.sequence().toFloat()) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.backerNumber)

            backing
                .filter { ObjectUtils.isNotNull(it.pledgedAt()) }
                .map { DateTimeUtils.longDate(ObjectUtils.requireNonNull(it.pledgedAt())) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.pledgeDate)

            backing
                .map { it.amount() - it.shippingAmount() - it.bonusAmount() }
                .filter { ObjectUtils.isNotNull(it) }
                .compose<Pair<Double, Project>>(combineLatestPair(backedProject))
                .map { ProjectViewUtils.styleCurrency(it.first, it.second, this.ksCurrency) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.pledgeAmount)

            backing
                .map {
                    shouldHideShipping(it)
                }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe {
                    this.shippingSummaryIsGone.onNext(it)
                }

            backing
                .map { ObjectUtils.isNull(it.reward()) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe {
                    this.pledgeSummaryIsGone.onNext(it)
                }

            Observable.combineLatest(backedProject, backing, this.currentUser.loggedInUser()) { p, b, user -> Triple(p, b, user) }
                .map { pledgeStatusData(it.first, it.second, it.third) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.pledgeStatusData)

            backing
                .map { it.shippingAmount() }
                .filter { ObjectUtils.isNotNull(it) }
                .compose<Pair<Float, Project>>(combineLatestPair(backedProject))
                .map { ProjectViewUtils.styleCurrency(it.first.toDouble(), it.second, this.ksCurrency) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.shippingAmount)

            backing
                .map { it.locationName()?.let { name -> name } }
                .filter { ObjectUtils.isNotNull(it) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.shippingLocation)

            backing
                .map { it.amount() }
                .filter { ObjectUtils.isNotNull(it) }
                .compose<Pair<Double, Project>>(combineLatestPair(backedProject))
                .map { ProjectViewUtils.styleCurrency(it.first, it.second, this.ksCurrency) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.totalAmount)

            backing
                .map { it.paymentSource() }
                .map { CreditCardPaymentType.safeValueOf(it?.paymentType()) }
                .map { it == CreditCardPaymentType.ANDROID_PAY || it == CreditCardPaymentType.APPLE_PAY || it == CreditCardPaymentType.CREDIT_CARD }
                .map { it.negate() }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.paymentMethodIsGone)

            val paymentSource = backing
                .map { it.paymentSource() }
                .filter { ObjectUtils.isNotNull(it) }
                .ofType(PaymentSource::class.java)

            val simpleDateFormat = SimpleDateFormat(StoredCard.DATE_FORMAT, Locale.getDefault())

            paymentSource
                .map { source ->
                    source.expirationDate()?.let { simpleDateFormat.format(it) } ?: ""
                }
                .filter { ObjectUtils.isNotNull(it) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.cardExpiration)

            paymentSource
                .map { cardIssuer(it) }
                .filter { ObjectUtils.isNotNull(it) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.cardIssuer)

            paymentSource
                .map { it.lastFour() ?: "" }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.cardLastFour)

            paymentSource
                .map { cardLogo(it) }
                .filter { ObjectUtils.isNotNull(it) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.cardLogo)

            val backingIsNotErrored = backing
                .map { it.isErrored() }
                .distinctUntilChanged()
                .map { it.negate() }

            backingIsNotErrored
                .compose(bindToLifecycle())
                .subscribe { this.fixPaymentMethodButtonIsGone.onNext(it) }

            backingIsNotErrored
                .compose(bindToLifecycle())
                .subscribe { this.fixPaymentMethodMessageIsGone.onNext(it) }

            this.fixPaymentMethodButtonClicked
                .compose(bindToLifecycle())
                .subscribe { this.notifyDelegateToShowFixPledge.onNext(null) }

            backing
                .map { it.completedByBacker() }
                .distinctUntilChanged()
                .compose(bindToLifecycle<Boolean>())
                .subscribe(this.receivedCheckboxChecked)

            backing
                .compose<Pair<Backing, Project>>(combineLatestPair(backedProject))
                .compose(takePairWhen<Pair<Backing, Project>, Boolean>(this.receivedCheckboxToggled))
                .switchMap { this.apiClient.postBacking(it.first.second, it.first.first, it.second).compose(neverError()) }
                .compose(bindToLifecycle())
                .share()
                .subscribe()

            this.isExpanded
                .filter { it }
                .compose(combineLatestPair(backing))
                .map { it.second }
                .compose<Pair<Backing, ProjectData>>(combineLatestPair(projectDataInput))
                .compose(bindToLifecycle())
                .subscribe {
                    this.analyticEvents.trackManagePledgePageViewed(it.first, it.second)
                }

            val rewardIsReceivable = reward
                .map {
                    RewardUtils.isReward(it) && ObjectUtils.isNotNull(it.estimatedDeliveryOn())
                }

            val backingIsCollected = backing
                .map { it.status() }
                .map { it == Backing.STATUS_COLLECTED }
                .distinctUntilChanged()

            val sectionShouldBeGone = rewardIsReceivable
                .compose(combineLatestPair<Boolean, Boolean>(backingIsCollected))
                .map { it.first && it.second }
                .map { it.negate() }
                .distinctUntilChanged()

            sectionShouldBeGone
                .compose<Pair<Boolean, Boolean>>(combineLatestPair(isCreator))
                .compose(bindToLifecycle())
                .subscribe {
                    val isUserCreator = it.second
                    val shouldBeGone = it.first

                    if (isUserCreator) {
                        this.receivedSectionIsGone.onNext(true)
                        this.receivedSectionCreatorIsGone.onNext(shouldBeGone)
                    } else {
                        this.receivedSectionIsGone.onNext(shouldBeGone)
                        this.receivedSectionCreatorIsGone.onNext(true)
                    }
                }

            this.refreshProject
                .compose(bindToLifecycle())
                .subscribe {
                    this.notifyDelegateToRefreshProject.onNext(null)
                    this.swipeRefresherProgressIsVisible.onNext(true)
                }

            val refreshTimeout = this.notifyDelegateToRefreshProject
                .delay(10, TimeUnit.SECONDS)

            Observable.merge(refreshTimeout, backedProject.skip(1))
                .map { false }
                .compose(bindToLifecycle())
                .subscribe(this.swipeRefresherProgressIsVisible)

            val addOns = backing
                .map { it.addOns()?.toList() ?: emptyList() }
                .compose(bindToLifecycle())

            projectDataInput
                .compose<Pair<ProjectData, List<Reward>>>(combineLatestPair(addOns))
                .compose(bindToLifecycle())
                .subscribe(this.addOnsList)

            backing
                .map { it.bonusAmount() }
                .filter { ObjectUtils.isNotNull(it) }
                .compose<Pair<Double, Project>>(combineLatestPair(backedProject))
                .map { ProjectViewUtils.styleCurrency(it.first, it.second, this.ksCurrency) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.bonusSupport)

            reward
                .filter { RewardUtils.isReward(it) && ObjectUtils.isNotNull(it.estimatedDeliveryOn()) }
                .map<DateTime> { it.estimatedDeliveryOn() }
                .map { DateTimeUtils.estimatedDeliveryOn(it) }
                .compose(bindToLifecycle())
                .subscribe(this.estimatedDelivery)

            isCreator
                .compose(bindToLifecycle())
                .subscribe(this.deliveryDisclaimerSectionIsGone)
        }

        private fun shouldHideShipping(it: Backing) =
            ObjectUtils.isNull(it.locationId()) || it.reward()?.let { rw ->
                RewardUtils.isLocalPickup(rw)
            } ?: true

        private fun getBackingInfo(it: ProjectData): Observable<Backing> {
            return if (it.backing() == null) {
                this.apolloClient.getProjectBacking(it.project().slug() ?: "")
            } else {
                Observable.just(it.backing())
            }
        }

        private fun joinProjectDataAndReward(projectData: ProjectData): Pair<ProjectData, Reward> {
            val reward = projectData.backing()?.reward()
                ?: projectData.project().backing()?.backedReward(projectData.project())
                ?: RewardFactory.noReward().toBuilder()
                    .minimum(projectData.backing()?.amount() ?: 1.0)
                    .build()

            return Pair(projectData, reward)
        }

        private fun cardIssuer(paymentSource: PaymentSource): Either<String, Int> {
            return when (CreditCardPaymentType.safeValueOf(paymentSource.paymentType())) {
                CreditCardPaymentType.ANDROID_PAY -> Either.Right(R.string.googlepay_button_content_description)
                CreditCardPaymentType.APPLE_PAY -> Either.Right(R.string.apple_pay_content_description)
                CreditCardPaymentType.CREDIT_CARD -> Either.Left(StoredCard.issuer(CreditCardTypes.safeValueOf(paymentSource.type())))
                else -> Either.Left(CardBrand.Unknown.code)
            }
        }

        private fun cardLogo(paymentSource: PaymentSource): Int {
            return when (CreditCardPaymentType.safeValueOf(paymentSource.paymentType())) {
                CreditCardPaymentType.ANDROID_PAY -> R.drawable.google_pay_mark
                CreditCardPaymentType.APPLE_PAY -> R.drawable.apple_pay_mark
                CreditCardPaymentType.CREDIT_CARD -> paymentSource.getCardTypeDrawable()
                else -> R.drawable.generic_bank_md
            }
        }

        private fun pledgeStatusData(project: Project, backing: Backing, user: User): PledgeStatusData {

            var statusStringRes: Int?

            if (!project.userIsCreator(user)) {
                statusStringRes = when (project.state()) {
                    Project.STATE_CANCELED -> R.string.The_creator_canceled_this_project_so_your_payment_method_was_never_charged
                    Project.STATE_FAILED -> R.string.This_project_didnt_reach_its_funding_goal_so_your_payment_method_was_never_charged
                    else -> when (backing.status()) {
                        Backing.STATUS_CANCELED -> R.string.You_canceled_your_pledge_for_this_project
                        Backing.STATUS_COLLECTED -> R.string.We_collected_your_pledge_for_this_project
                        Backing.STATUS_DROPPED -> R.string.Your_pledge_was_dropped_because_of_payment_errors
                        Backing.STATUS_ERRORED -> R.string.We_cant_process_your_pledge_Please_update_your_payment_method
                        Backing.STATUS_PLEDGED -> R.string.If_the_project_reaches_its_funding_goal_you_will_be_charged_total_on_project_deadline
                        Backing.STATUS_PREAUTH -> R.string.We_re_processing_your_pledge_pull_to_refresh
                        else -> null
                    }
                }
            } else {

                statusStringRes = when (project.state()) {
                    Project.STATE_CANCELED -> R.string.You_canceled_this_project_so_the_backers_payment_method_was_never_charged
                    Project.STATE_FAILED -> R.string.Your_project_didnt_reach_its_funding_goal_so_the_backers_payment_method_was_never_charged
                    else -> when (backing.status()) {
                        Backing.STATUS_CANCELED -> R.string.The_backer_canceled_their_pledge_for_this_project
                        Backing.STATUS_COLLECTED -> R.string.We_collected_the_backers_pledge_for_this_project
                        Backing.STATUS_DROPPED -> R.string.This_pledge_was_dropped_because_of_payment_errors
                        Backing.STATUS_ERRORED -> R.string.We_cant_process_this_pledge_because_of_a_problem_with_the_backers_payment_method
                        Backing.STATUS_PLEDGED -> R.string.If_your_project_reaches_its_funding_goal_the_backer_will_be_charged_total_on_project_deadline
                        Backing.STATUS_PREAUTH -> R.string.We_re_processing_this_pledge_pull_to_refresh
                        else -> null
                    }
                }
            }

            val projectDeadline = project.deadline()?.let { DateTimeUtils.longDate(it) }
            val pledgeTotal = backing.amount()
            val pledgeTotalString = this.ksCurrency.format(pledgeTotal, project)

            return PledgeStatusData(statusStringRes, pledgeTotalString, projectDeadline)
        }

        override fun configureWith(projectData: ProjectData) {
            this.projectDataInput.onNext(projectData)
        }

        override fun fixPaymentMethodButtonClicked() {
            this.fixPaymentMethodButtonClicked.onNext(null)
        }

        override fun pledgeSuccessfullyUpdated() {
            this.showUpdatePledgeSuccess.onNext(null)
        }

        override fun receivedCheckboxToggled(checked: Boolean) {
            this.receivedCheckboxToggled.onNext(checked)
        }

        override fun refreshProject() {
            this.refreshProject.onNext(null)
        }

        override fun backerAvatar(): Observable<String> = this.backerAvatar

        override fun backerName(): Observable<String> = this.backerName

        override fun backerNumber(): Observable<String> = this.backerNumber

        override fun cardExpiration(): Observable<String> = this.cardExpiration

        override fun cardIssuer(): Observable<Either<String, Int>> = this.cardIssuer

        override fun cardLastFour(): Observable<String> = this.cardLastFour

        override fun cardLogo(): Observable<Int> = this.cardLogo

        override fun fixPaymentMethodButtonIsGone(): Observable<Boolean> = this.fixPaymentMethodButtonIsGone

        override fun fixPaymentMethodMessageIsGone(): Observable<Boolean> = this.fixPaymentMethodMessageIsGone

        override fun notifyDelegateToRefreshProject(): Observable<Void> = this.notifyDelegateToRefreshProject

        override fun notifyDelegateToShowFixPledge(): Observable<Void> = this.notifyDelegateToShowFixPledge

        override fun paymentMethodIsGone(): Observable<Boolean> = this.paymentMethodIsGone

        override fun pledgeAmount(): Observable<CharSequence> = this.pledgeAmount

        override fun pledgeDate(): Observable<String> = this.pledgeDate

        override fun pledgeStatusData(): Observable<PledgeStatusData> = this.pledgeStatusData

        override fun pledgeSummaryIsGone(): Observable<Boolean> = this.pledgeSummaryIsGone

        override fun projectDataAndReward(): Observable<Pair<ProjectData, Reward>> = this.projectDataAndReward

        override fun projectDataAndAddOns(): Observable<Pair<ProjectData, List<Reward>>> = this.addOnsList

        override fun receivedCheckboxChecked(): Observable<Boolean> = this.receivedCheckboxChecked

        override fun receivedSectionIsGone(): Observable<Boolean> = this.receivedSectionIsGone

        override fun receivedSectionCreatorIsGone(): Observable<Boolean> = this.receivedSectionCreatorIsGone

        override fun shippingAmount(): Observable<CharSequence> = this.shippingAmount

        override fun shippingLocation(): Observable<String> = this.shippingLocation

        override fun shippingSummaryIsGone(): Observable<Boolean> = this.shippingSummaryIsGone

        override fun showUpdatePledgeSuccess(): Observable<Void> = this.showUpdatePledgeSuccess

        override fun swipeRefresherProgressIsVisible(): Observable<Boolean> = this.swipeRefresherProgressIsVisible

        override fun totalAmount(): Observable<CharSequence> = this.totalAmount

        override fun bonusSupport(): Observable<CharSequence> = this.bonusSupport

        override fun estimatedDelivery(): Observable<String> = this.estimatedDelivery

        override fun deliveryDisclaimerSectionIsGone(): Observable<Boolean> = this.deliveryDisclaimerSectionIsGone
    }
}
