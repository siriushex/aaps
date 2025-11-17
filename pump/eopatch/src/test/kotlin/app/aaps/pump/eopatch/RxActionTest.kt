package app.aaps.pump.eopatch

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.observers.TestObserver
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.util.concurrent.TimeUnit

class RxActionTest {

    @Mock
    private lateinit var aapsSchedulers: AapsSchedulers

    @Mock
    private lateinit var aapsLogger: AAPSLogger

    private lateinit var rxAction: RxAction
    private lateinit var testScheduler: TestScheduler

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testScheduler = TestScheduler()

        `when`(aapsSchedulers.main).thenReturn(testScheduler)

        rxAction = RxAction(aapsSchedulers, aapsLogger)
    }

    @Test
    fun `RxVoid should have INSTANCE value`() {
        assertThat(RxAction.RxVoid.INSTANCE).isNotNull()
    }

    @Test
    fun `RxVoid should be an enum with single value`() {
        val values = RxAction.RxVoid.entries
        assertThat(values).hasSize(1)
        assertThat(values[0]).isEqualTo(RxAction.RxVoid.INSTANCE)
    }

    @Test
    fun `single should create observable that completes action`() {
        var actionExecuted = false
        val action = Runnable { actionExecuted = true }

        val testObserver = TestObserver<Any>()
        rxAction.single(action, 0, Schedulers.trampoline())
            .subscribe(testObserver)

        assertThat(actionExecuted).isTrue()
        testObserver.assertComplete()
        testObserver.assertNoErrors()
    }

    @Test
    fun `single should execute action with delay`() {
        var actionExecuted = false
        val action = Runnable { actionExecuted = true }
        val delayMs = 100L

        val testObserver = TestObserver<Any>()
        rxAction.single(action, delayMs, testScheduler)
            .subscribe(testObserver)

        // Action should not be executed yet
        assertThat(actionExecuted).isFalse()

        // Advance time
        testScheduler.advanceTimeBy(delayMs, TimeUnit.MILLISECONDS)

        // Now action should be executed
        assertThat(actionExecuted).isTrue()
        testObserver.assertComplete()
    }

    @Test
    fun `single should execute action immediately when delay is 0`() {
        var actionExecuted = false
        val action = Runnable { actionExecuted = true }

        val testObserver = TestObserver<Any>()
        rxAction.single(action, 0, Schedulers.trampoline())
            .subscribe(testObserver)

        assertThat(actionExecuted).isTrue()
        testObserver.assertComplete()
    }

    @Test
    fun `single should execute action immediately when delay is negative`() {
        var actionExecuted = false
        val action = Runnable { actionExecuted = true }

        val testObserver = TestObserver<Any>()
        rxAction.single(action, -100, Schedulers.trampoline())
            .subscribe(testObserver)

        assertThat(actionExecuted).isTrue()
        testObserver.assertComplete()
    }

    @Test
    fun `single should return RxVoid INSTANCE on completion`() {
        val action = Runnable { }

        val testObserver = TestObserver<Any>()
        rxAction.single(action, 0, Schedulers.trampoline())
            .subscribe(testObserver)

        testObserver.assertValue(RxAction.RxVoid.INSTANCE)
    }

    @Test
    fun `runOnMainThread should execute action on main scheduler`() {
        var actionExecuted = false
        val action = Runnable { actionExecuted = true }

        rxAction.runOnMainThread(action)

        // Action should not be executed yet since we're using testScheduler
        assertThat(actionExecuted).isFalse()

        // Advance the scheduler
        testScheduler.triggerActions()

        assertThat(actionExecuted).isTrue()
    }

    @Test
    fun `runOnMainThread should execute action with delay`() {
        var actionExecuted = false
        val action = Runnable { actionExecuted = true }
        val delayMs = 100L

        rxAction.runOnMainThread(action, delayMs)

        // Action should not be executed yet
        assertThat(actionExecuted).isFalse()

        // Advance time
        testScheduler.advanceTimeBy(delayMs, TimeUnit.MILLISECONDS)

        assertThat(actionExecuted).isTrue()
    }

    @Test
    fun `runOnMainThread should execute action immediately when delay is 0`() {
        var actionExecuted = false
        val action = Runnable { actionExecuted = true }

        rxAction.runOnMainThread(action, 0)

        // Trigger immediate actions
        testScheduler.triggerActions()

        assertThat(actionExecuted).isTrue()
    }

    @Test
    fun `runOnMainThread should handle action exceptions gracefully`() {
        val action = Runnable { throw RuntimeException("Test exception") }

        // Should not throw - errors are logged via aapsLogger
        rxAction.runOnMainThread(action, 0)
        testScheduler.triggerActions()

        // Test passes if no exception propagates
    }

    @Test
    fun `single should use provided scheduler`() {
        var actionExecuted = false
        val action = Runnable { actionExecuted = true }
        val customScheduler = TestScheduler()

        val testObserver = TestObserver<Any>()
        rxAction.single(action, 0, customScheduler)
            .subscribe(testObserver)

        // Action should not be executed until custom scheduler triggers
        assertThat(actionExecuted).isFalse()

        customScheduler.triggerActions()

        assertThat(actionExecuted).isTrue()
    }

    @Test
    fun `multiple runOnMainThread calls should execute in order`() {
        val executionOrder = mutableListOf<Int>()

        rxAction.runOnMainThread(Runnable { executionOrder.add(1) }, 0)
        rxAction.runOnMainThread(Runnable { executionOrder.add(2) }, 0)
        rxAction.runOnMainThread(Runnable { executionOrder.add(3) }, 0)

        testScheduler.triggerActions()

        assertThat(executionOrder).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `runOnMainThread with different delays should execute in delay order`() {
        val executionOrder = mutableListOf<Int>()

        rxAction.runOnMainThread(Runnable { executionOrder.add(3) }, 300)
        rxAction.runOnMainThread(Runnable { executionOrder.add(1) }, 100)
        rxAction.runOnMainThread(Runnable { executionOrder.add(2) }, 200)

        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        assertThat(executionOrder).containsExactly(1)

        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        assertThat(executionOrder).containsExactly(1, 2).inOrder()

        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        assertThat(executionOrder).containsExactly(1, 2, 3).inOrder()
    }
}
