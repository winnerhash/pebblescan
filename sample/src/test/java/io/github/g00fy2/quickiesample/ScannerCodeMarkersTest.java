package io.github.g00fy2.quickiesample;

import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import io.github.g00fy2.quickie.CodeMarker;
import io.github.g00fy2.quickie.QROverlayView;
import kotlin.Unit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.Collections;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35}, qualifiers = "w393dp-h800dp")
public class ScannerCodeMarkersTest {
  private ActivityController<MainActivity> activity;
  private QROverlayView overlay;
  private FrameLayout markers;
  private int selected = -1;
  private int focused;
  private int scaled;

  @Before
  public void setUp() {
    activity = Robolectric.buildActivity(MainActivity.class, new Intent(MainActivity.ACTION_HOME)).setup();
    overlay = new QROverlayView(activity.get());
    overlay.setPadding(12, 24, 12, 20);
    overlay.setFocusAction((x, y) -> { focused++; return Unit.INSTANCE; });
    overlay.setZoomActions(6f, ratio -> Unit.INSTANCE, factor -> { scaled++; return Unit.INSTANCE; }, () -> Unit.INSTANCE);
    activity.get().setContentView(overlay);
    markers = overlay.findViewById(io.github.g00fy2.quickie.R.id.code_markers);
    layout();
    overlay.setCodeChoices(Arrays.asList(new CodeMarker(10, .25f, .4f), new CodeMarker(20, .75f, .6f)), id -> {
      selected = id;
      return Unit.INSTANCE;
    });
    layout();
  }

  @After
  public void tearDown() {
    activity.pause().stop().destroy();
  }

  @Test
  public void dotsAlignWithCameraPositionsIncludingInsetsAndSelectTheirOwnResult() {
    assertEquals(2, markers.getChildCount());
    View left = markers.getChildAt(0);
    View right = markers.getChildAt(1);
    assertEquals(393f * .25f, markers.getLeft() + left.getLeft() + left.getWidth() / 2f, 1f);
    assertEquals(800f * .4f, markers.getTop() + left.getTop() + left.getHeight() / 2f, 1f);
    assertTrue(left.getWidth() >= 48);
    assertEquals(View.VISIBLE, overlay.findViewById(io.github.g00fy2.quickie.R.id.selection_hint).getVisibility());
    right.performClick();
    assertEquals(20, selected);
    assertEquals(0, focused);
  }

  @Test
  public void anUpdateDuringTouchCannotSwapTheResultUnderTheFinger() {
    View pressed = markers.getChildAt(0);
    long time = SystemClock.uptimeMillis();
    touch(pressed, time, MotionEvent.ACTION_DOWN);
    overlay.setCodeChoices(Collections.singletonList(new CodeMarker(30, .25f, .4f)), id -> {
      selected = id;
      return Unit.INSTANCE;
    });
    assertEquals(2, markers.getChildCount());
    touch(pressed, time, MotionEvent.ACTION_UP);
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(10, selected);
    assertEquals(1, markers.getChildCount());
    markers.getChildAt(0).performClick();
    assertEquals(30, selected);
  }

  @Test
  public void anOldCancelledTapTimerCannotRebindMarkersDuringTheNextTap() {
    View pressed = markers.getChildAt(0);
    long first = SystemClock.uptimeMillis();
    touch(pressed, first, MotionEvent.ACTION_DOWN);
    MotionEvent outside = MotionEvent.obtain(first, first + 10, MotionEvent.ACTION_MOVE, -80f, -80f, 0);
    pressed.dispatchTouchEvent(outside);
    outside.recycle();
    touch(pressed, first, MotionEvent.ACTION_UP);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
    long second = SystemClock.uptimeMillis();
    touch(pressed, second, MotionEvent.ACTION_DOWN);
    overlay.setCodeChoices(Collections.singletonList(new CodeMarker(30, .25f, .4f)), id -> Unit.INSTANCE);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
    assertEquals(2, markers.getChildCount());
    touch(pressed, second, MotionEvent.ACTION_UP);
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(10, selected);
  }

  @Test
  public void aPinchStartingOnADotScalesWithoutSelectingOrFocusing() {
    long down = SystemClock.uptimeMillis();
    pointers(down, down, MotionEvent.ACTION_DOWN, 1, 200f);
    pointers(down, down + 20, MotionEvent.ACTION_POINTER_DOWN | (1 << 8), 2, 200f);
    pointers(down, down + 40, MotionEvent.ACTION_MOVE, 2, 250f);
    pointers(down, down + 60, MotionEvent.ACTION_MOVE, 2, 310f);
    pointers(down, down + 80, MotionEvent.ACTION_POINTER_UP | (1 << 8), 2, 310f);
    pointers(down, down + 100, MotionEvent.ACTION_UP, 1, 310f);
    shadowOf(Looper.getMainLooper()).idle();
    assertTrue(scaled > 0);
    assertEquals(-1, selected);
    assertEquals(0, focused);
  }

  @Test
  public void removingChoicesRemovesTheirClickTargetsAndHint() {
    overlay.setCodeChoices(Collections.emptyList(), id -> { selected = id; return Unit.INSTANCE; });
    assertEquals(0, markers.getChildCount());
    assertEquals(View.GONE, overlay.findViewById(io.github.g00fy2.quickie.R.id.selection_hint).getVisibility());
    assertEquals(-1, selected);
  }

  @Test
  public void physicalMarkerCoordinatesAreNotMirroredByRightToLeftLayout() {
    overlay.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    layout();
    overlay.setCodeChoices(Collections.singletonList(new CodeMarker(10, .25f, .4f)), id -> Unit.INSTANCE);
    layout();
    View left = markers.getChildAt(0);
    assertEquals(393f * .25f, markers.getLeft() + left.getLeft() + left.getWidth() / 2f, 1f);
  }

  private void layout() {
    overlay.measure(
        View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
    overlay.layout(0, 0, 393, 800);
  }

  private void touch(View view, long down, int action) {
    MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 24f, 24f, 0);
    view.dispatchTouchEvent(event);
    event.recycle();
  }

  private void pointers(long down, long time, int action, int count, float secondX) {
    MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
    MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[count];
    for (int index = 0; index < count; index++) {
      properties[index] = new MotionEvent.PointerProperties();
      properties[index].id = index;
      properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;
      coordinates[index] = new MotionEvent.PointerCoords();
      coordinates[index].x = index == 0 ? 98f : secondX;
      coordinates[index].y = 320f;
      coordinates[index].pressure = 1f;
      coordinates[index].size = 1f;
    }
    MotionEvent event = MotionEvent.obtain(down, time, action, count, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0);
    overlay.dispatchTouchEvent(event);
    event.recycle();
  }
}
