package io.github.g00fy2.quickiesample;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
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

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35}, qualifiers = "w393dp-h800dp")
public class ScannerZoomControlsTest {
  private ActivityController<MainActivity> activity;
  private QROverlayView overlay;
  private int taps;
  private int scales;
  private boolean resumed;
  private float lastRatio;
  private View button;
  private View fan;

  @Before
  public void setUp() {
    activity = Robolectric.buildActivity(MainActivity.class, new Intent(MainActivity.ACTION_HOME)).setup();
    overlay = new QROverlayView(activity.get());
    activity.get().setContentView(overlay);
    overlay.setFocusAction((x, y) -> { taps++; return Unit.INSTANCE; });
    overlay.setGalleryVisibilityAndOnClick(true, () -> Unit.INSTANCE);
    overlay.setTorchVisibilityAndOnClick(true, enabled -> Unit.INSTANCE);
    overlay.setPadding(12, 24, 12, 20);
    overlay.setZoomActions(
        6f,
        ratio -> { lastRatio = ratio; overlay.setZoomState(ratio, true); return Unit.INSTANCE; },
        factor -> { scales++; return Unit.INSTANCE; },
        () -> { resumed = true; return Unit.INSTANCE; });
    overlay.measure(
        View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
    overlay.layout(0, 0, 393, 800);
    button = overlay.findViewById(io.github.g00fy2.quickie.R.id.zoom_button);
    fan = overlay.findViewById(io.github.g00fy2.quickie.R.id.zoom_fan);
    overlay.setZoomState(1f, false);
  }

  @After
  public void tearDown() {
    activity.pause().stop().destroy();
  }

  @Test
  public void holdingOpensFanWithoutChangingCurrentZoomAndReleaseKeepsIt() {
    overlay.setZoomState(3f, true);
    gesture(MotionEvent.ACTION_DOWN, 0f, 0f);
    assertTrue(overlay.isZoomInteracting());
    assertEquals(View.GONE, fan.getVisibility());
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
    assertEquals(View.VISIBLE, fan.getVisibility());
    assertEquals(0f, overlay.findViewById(io.github.g00fy2.quickie.R.id.bottom_controls).getAlpha(), 0f);
    assertEquals(0f, lastRatio, 0f);
    gesture(MotionEvent.ACTION_MOVE, 2f, 1f);
    assertEquals(0f, lastRatio, 0f);
    gesture(MotionEvent.ACTION_UP, 0f, 0f);
    assertEquals(View.GONE, fan.getVisibility());
    assertEquals(1f, overlay.findViewById(io.github.g00fy2.quickie.R.id.bottom_controls).getAlpha(), 0f);
    assertFalse(overlay.isZoomInteracting());
    assertFalse(resumed);
    assertEquals(0, taps);
  }

  @Test
  public void slideOutsideButtonContinuesZoomingAndCanReverseBeforeRelease() {
    gesture(MotionEvent.ACTION_DOWN, 0f, 0f);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
    gesture(MotionEvent.ACTION_MOVE, 68f, 28f);
    assertTrue(lastRatio > 5.9f);
    assertTrue(lastRatio <= 6f);
    gesture(MotionEvent.ACTION_MOVE, 0f, 0f);
    assertEquals(1f, lastRatio, 0.001f);
    gesture(MotionEvent.ACTION_UP, 0f, 0f);
    assertFalse(resumed);
    assertEquals(0, taps);
  }

  @Test
  public void shortTapResumesAutomaticZoomAndCancelledHoldNeverClicks() {
    gesture(MotionEvent.ACTION_DOWN, 0f, 0f);
    gesture(MotionEvent.ACTION_UP, 0f, 0f);
    assertTrue(resumed);
    resumed = false;
    gesture(MotionEvent.ACTION_DOWN, 0f, 0f);
    gesture(MotionEvent.ACTION_CANCEL, 0f, 0f);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    assertEquals(View.GONE, fan.getVisibility());
    assertFalse(overlay.isZoomInteracting());
    assertFalse(resumed);
  }

  @Test
  public void addingSecondFingerClosesFanAndCannotTurnReleaseIntoClick() {
    gesture(MotionEvent.ACTION_DOWN, 0f, 0f);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
    MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
    MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[2];
    for (int index = 0; index < 2; index++) {
      properties[index] = new MotionEvent.PointerProperties();
      properties[index].id = index;
      coordinates[index] = new MotionEvent.PointerCoords();
      coordinates[index].x = button.getWidth() / 2f + index * 80f;
      coordinates[index].y = button.getHeight() / 2f;
    }
    long now = SystemClock.uptimeMillis();
    MotionEvent secondFinger = MotionEvent.obtain(
        now, now, MotionEvent.ACTION_POINTER_DOWN | (1 << 8), 2, properties, coordinates,
        0, 0, 1f, 1f, 0, 0, 0, 0);
    button.dispatchTouchEvent(secondFinger);
    secondFinger.recycle();
    assertEquals(View.GONE, fan.getVisibility());
    assertFalse(overlay.isZoomInteracting());
    gesture(MotionEvent.ACTION_UP, 0f, 0f);
    assertFalse(resumed);
    assertEquals(0, taps);
  }

  @Test
  public void keyboardAndAccessibilityCanAdjustZoomWithinCameraLimits() {
    button.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
    assertEquals(1.1f, lastRatio, 0.001f);
    Bundle arguments = new Bundle();
    arguments.putFloat(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE, 20f);
    assertTrue(button.performAccessibilityAction(AccessibilityActionCompat.ACTION_SET_PROGRESS.getId(), arguments));
    assertEquals(6f, lastRatio, 0.001f);
    arguments.putFloat(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE, Float.NaN);
    assertFalse(button.performAccessibilityAction(AccessibilityActionCompat.ACTION_SET_PROGRESS.getId(), arguments));
    assertEquals(6f, lastRatio, 0.001f);
  }

  @Test
  public void pinchDoesNotBecomeFocusTapAndNextSingleTapStillFocuses() {
    long down = SystemClock.uptimeMillis();
    touch(down, down, MotionEvent.ACTION_DOWN, 1, 100f);
    touch(down, down + 20, MotionEvent.ACTION_POINTER_DOWN | (1 << 8), 2, 180f);
    touch(down, down + 40, MotionEvent.ACTION_MOVE, 2, 220f);
    touch(down, down + 60, MotionEvent.ACTION_MOVE, 2, 280f);
    touch(down, down + 80, MotionEvent.ACTION_POINTER_UP | (1 << 8), 2, 280f);
    touch(down, down + 100, MotionEvent.ACTION_UP, 1, 100f);
    assertTrue(scales > 0);
    assertEquals(0, taps);
    touch(down + 500, down + 500, MotionEvent.ACTION_DOWN, 1, 100f);
    touch(down + 500, down + 550, MotionEvent.ACTION_UP, 1, 100f);
    assertEquals(1, taps);
  }

  private void touch(long down, long time, int action, int count, float secondX) {
    MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
    MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[count];
    for (int index = 0; index < count; index++) {
      properties[index] = new MotionEvent.PointerProperties();
      properties[index].id = index;
      properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;
      coordinates[index] = new MotionEvent.PointerCoords();
      coordinates[index].x = index == 0 ? 100f : secondX;
      coordinates[index].y = 300f;
      coordinates[index].pressure = 1f;
      coordinates[index].size = 1f;
    }
    MotionEvent event = MotionEvent.obtain(down, time, action, count, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0);
    overlay.onTouchEvent(event);
    event.recycle();
  }

  private void gesture(int action, float dx, float dy) {
    int[] location = new int[2];
    button.getLocationOnScreen(location);
    long time = SystemClock.uptimeMillis();
    MotionEvent event = MotionEvent.obtain(
        time, time, action, location[0] + button.getWidth() / 2f + dx,
        location[1] + button.getHeight() / 2f + dy, 0);
    overlay.dispatchTouchEvent(event);
    event.recycle();
  }
}
