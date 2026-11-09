package io.github.g00fy2.quickiesample;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Size;
import android.view.View;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import androidx.camera.view.PreviewView;
import androidx.camera.view.TransformExperimental;
import androidx.camera.view.transform.OutputTransform;
import io.github.g00fy2.quickie.CameraCodeSelection;
import io.github.g00fy2.quickie.CodePosition;
import io.github.g00fy2.quickie.DetectedScanFrame;
import io.github.g00fy2.quickie.FrozenPreviewView;
import io.github.g00fy2.quickie.QROverlayView;
import io.github.g00fy2.quickie.ScanCode;
import kotlin.Unit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.time.Duration;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34}, qualifiers = "w393dp-h800dp-mdpi", shadows = {ScannerFrozenSelectionTest.PreviewTransform.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(markerClass = {TransformExperimental.class})
public class ScannerFrozenSelectionTest {
  @Implements(PreviewView.class)
  public static class PreviewTransform extends org.robolectric.shadows.ShadowViewGroup {
    static OutputTransform target;
    @Implementation protected OutputTransform getOutputTransform() { return target; }
  }

  private ActivityController<MainActivity> activity;
  private QROverlayView overlay;
  private FrozenPreviewView frozen;
  private CameraCodeSelection selection;
  private OutputTransform transform;
  private FrameLayout root;
  private boolean paused;
  private String selected;
  private int captures;
  private long frame;

  @Before public void setUp() {
    activity = Robolectric.buildActivity(MainActivity.class, new Intent(MainActivity.ACTION_HOME)).setup();
    Matrix matrix = new Matrix();
    matrix.setScale(393f / 2f, 800f / 2f);
    matrix.postTranslate(393f / 2f, 800f / 2f);
    transform = new OutputTransform(matrix, new Size(393, 800));
    PreviewTransform.target = transform;
    PreviewView preview = new PreviewView(activity.get());
    overlay = new QROverlayView(activity.get());
    overlay.setZoomActions(6f, value -> Unit.INSTANCE, value -> Unit.INSTANCE, () -> Unit.INSTANCE);
    frozen = new FrozenPreviewView(activity.get());
    frozen.setVisibility(View.GONE);
    root = new FrameLayout(activity.get());
    root.addView(preview);
    root.addView(frozen);
    root.addView(overlay);
    activity.get().setContentView(root);
    root.measure(View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, 393, 800);
    selection = new CameraCodeSelection(preview, overlay, frozen,
        value -> { paused = value; return Unit.INSTANCE; },
        code -> { selected = code.getText(); return Unit.INSTANCE; });
  }

  @After public void tearDown() { activity.pause().stop().destroy(); }

  @Test public void freezesTheDecodedFrameAndDotsStaySelectableAfterCameraMovesAway() throws Exception {
    List<ScanCode> codes = Arrays.asList(code("left", .25f, .4f), code("right", .75f, .6f));
    send(codes);
    assertFalse(frozen.isFrozen());
    advance(100);
    send(codes);
    assertTrue(frozen.isFrozen());
    assertTrue(paused);
    assertEquals(1, captures);
    assertEquals(View.GONE, overlay.findViewById(io.github.g00fy2.quickie.R.id.live_controls).getVisibility());
    assertEquals(View.VISIBLE, overlay.findViewById(io.github.g00fy2.quickie.R.id.resume_scanning).getVisibility());
    advance(5000);
    send(Collections.emptyList());
    FrameLayout markers = overlay.findViewById(io.github.g00fy2.quickie.R.id.code_markers);
    assertEquals(2, markers.getChildCount());
    assertEquals(1, captures);
    Bitmap displayed = Bitmap.createBitmap(393, 800, Bitmap.Config.ARGB_8888);
    frozen.draw(new Canvas(displayed));
    assertEquals(Color.RED, displayed.getPixel(98, 320));
    assertEquals(Color.GREEN, displayed.getPixel(295, 480));
    root.measure(View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, 393, 800);
    assertEquals(98f, markers.getLeft() + markers.getChildAt(0).getLeft() + 24f, 1f);
    assertEquals(480f, markers.getTop() + markers.getChildAt(1).getTop() + 24f, 1f);
    Bitmap screenshot = Bitmap.createBitmap(393, 800, Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(screenshot));
    File directory = new File("build/reports/frozen-selection");
    assertTrue(directory.isDirectory() || directory.mkdirs());
    try (FileOutputStream output = new FileOutputStream(new File(directory, "preview.png"))) {
      assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
    }
    markers.getChildAt(1).performClick();
    assertEquals("right", selected);
  }

  @Test public void rescanDisarmsAnOldPressedDotBeforeReleasingTheSnapshot() {
    List<ScanCode> codes = Arrays.asList(code("a", .25f, .4f), code("b", .75f, .6f));
    send(codes);
    advance(100);
    send(codes);
    FrameLayout markers = overlay.findViewById(io.github.g00fy2.quickie.R.id.code_markers);
    View pressed = markers.getChildAt(0);
    long now = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 24f, 24f, 0);
    pressed.dispatchTouchEvent(down);
    down.recycle();
    overlay.findViewById(io.github.g00fy2.quickie.R.id.resume_scanning).performClick();
    assertEquals(0, markers.getChildCount());
    assertFalse(pressed.performClick());
    assertNull(selected);
    assertFalse(frozen.isFrozen());
  }

  @Test public void rescanClearsPictureAndTargetsAndSingleCodeCanScanAgain() {
    List<ScanCode> codes = Arrays.asList(code("a", .25f, .4f), code("b", .75f, .6f));
    send(codes);
    advance(100);
    send(codes);
    overlay.findViewById(io.github.g00fy2.quickie.R.id.resume_scanning).performClick();
    assertFalse(frozen.isFrozen());
    assertFalse(paused);
    assertEquals(View.GONE, frozen.getVisibility());
    assertEquals(0, ((FrameLayout) overlay.findViewById(io.github.g00fy2.quickie.R.id.code_markers)).getChildCount());
    assertEquals(View.VISIBLE, overlay.findViewById(io.github.g00fy2.quickie.R.id.live_controls).getVisibility());
    List<ScanCode> single = Collections.singletonList(code("new", .5f, .5f));
    send(single);
    advance(100);
    send(single);
    advance(260);
    send(single);
    assertEquals("new", selected);
    assertFalse(frozen.isFrozen());
    assertEquals(1, captures);
  }

  @Test public void waitsBrieflyForTheOtherCodeInsteadOfFreezingOnlyTheFirstResult() {
    List<ScanCode> partial = Arrays.asList(code("a", .25f, .4f), code(null, .75f, .6f));
    send(partial);
    advance(100);
    send(partial);
    assertFalse(frozen.isFrozen());
    List<ScanCode> complete = Arrays.asList(code("a", .25f, .4f), code("b", .75f, .6f));
    advance(100);
    send(complete);
    assertFalse(frozen.isFrozen());
    advance(100);
    send(complete);
    assertTrue(frozen.isFrozen());
    assertEquals(2, ((FrameLayout) overlay.findViewById(io.github.g00fy2.quickie.R.id.code_markers)).getChildCount());
  }

  @Test public void unsupportedCaptureKeepsLiveScanningAndDoesNotLeaveUnmatchedDots() {
    List<ScanCode> codes = Arrays.asList(code("a", .25f, .4f), code("b", .75f, .6f));
    send(codes);
    advance(100);
    selection.onFrame(new DetectedScanFrame(codes, transform, 393, 800, ++frame, 0),
        () -> { throw new UnsupportedOperationException("test conversion failure"); });
    assertFalse(frozen.isFrozen());
    assertFalse(paused);
    assertEquals(0, ((FrameLayout) overlay.findViewById(io.github.g00fy2.quickie.R.id.code_markers)).getChildCount());
    advance(100);
    send(codes);
    assertTrue(frozen.isFrozen());
  }

  private void send(List<ScanCode> codes) {
    selection.onFrame(new DetectedScanFrame(codes, transform, 393, 800, ++frame, 0), () -> {
      captures++;
      Bitmap image = Bitmap.createBitmap(393, 800, Bitmap.Config.ARGB_8888);
      image.eraseColor(Color.GRAY);
      Paint paint = new Paint();
      Canvas canvas = new Canvas(image);
      paint.setColor(Color.RED);
      canvas.drawRect(85, 305, 112, 335, paint);
      paint.setColor(Color.GREEN);
      canvas.drawRect(280, 465, 310, 495, paint);
      return image;
    });
  }

  private ScanCode code(String value, float x, float y) {
    return new ScanCode(new CodePosition(x, y, .1f, .1f), value, null, null);
  }

  private void advance(long milliseconds) {
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds));
  }
}
