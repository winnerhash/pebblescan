package io.github.g00fy2.quickiesample;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import io.github.g00fy2.quickie.QROverlayView;
import kotlin.Unit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadow.api.Shadow;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
// SDK 34 supports native rendering on Windows as well as macOS and Linux.
@Config(sdk = {34}, qualifiers = "zh-rCN-w393dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScannerZoomRenderingTest {
  @Test
  public void fanStaysNearThumbAndLeavesPreviewVisibleWithNormalAndLargeText() throws Exception {
    ActivityController<MainActivity> activity =
        Robolectric.buildActivity(MainActivity.class, new Intent(MainActivity.ACTION_HOME)).setup();
    try {
      for (float fontScale : new float[] {1f, 2f}) {
        RuntimeEnvironment.setFontScale(fontScale);
        QROverlayView overlay = new QROverlayView(activity.get());
        activity.get().setContentView(overlay);
        overlay.setPadding(12, 24, 12, 20);
        overlay.setGalleryVisibilityAndOnClick(true, () -> Unit.INSTANCE);
        overlay.setTorchVisibilityAndOnClick(true, enabled -> Unit.INSTANCE);
        overlay.setZoomActions(6f, ratio -> Unit.INSTANCE, factor -> Unit.INSTANCE, () -> Unit.INSTANCE);
        for (float ratio : new float[] {1f, 3f, 6f}) render(overlay, ratio, fontScale);
      }
    } finally {
      activity.pause().stop().destroy();
    }
  }

  private void render(QROverlayView overlay, float ratio, float fontScale) throws Exception {
    overlay.setZoomState(ratio, ratio != 1f);
    layout(overlay);
    View button = overlay.findViewById(io.github.g00fy2.quickie.R.id.zoom_button);
    MotionEvent down = MotionEvent.obtain(
        SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN,
        button.getWidth() / 2f, button.getHeight() / 2f, 0);
    button.dispatchTouchEvent(down);
    down.recycle();
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
    layout(overlay);
    Bitmap image = Bitmap.createBitmap(393, 800, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(image);
    assertTrue(Shadow.extract(canvas).getClass().getSimpleName().contains("Native"));
    canvas.drawColor(Color.rgb(135, 150, 144));
    overlay.draw(canvas);
    assertNotEquals(0xFFBFE9D6, image.getPixel(100, 100));
    int[] buttonPosition = new int[2];
    int[] overlayPosition = new int[2];
    button.getLocationOnScreen(buttonPosition);
    overlay.getLocationOnScreen(overlayPosition);
    int centerX = buttonPosition[0] - overlayPosition[0] + button.getWidth() / 2;
    int centerY = buttonPosition[1] - overlayPosition[1] + button.getHeight() / 2;
    int[] pixels = new int[393 * 800];
    image.getPixels(pixels, 0, 393, 0, 0, 393, 800);
    boolean found = false;
    for (int index = 0; index < pixels.length; index++) {
      if (pixels[index] != 0xFFBFE9D6) continue;
      found = true;
      assertTrue(Math.abs(index % 393 - centerX) <= 85);
      assertTrue(index / 393 >= centerY - 20 && index / 393 <= centerY + 50);
    }
    assertTrue(found);
    File directory = new File("build/reports/zoom-fan");
    assertTrue(directory.isDirectory() || directory.mkdirs());
    try (FileOutputStream output = new FileOutputStream(
        new File(directory, "preview-" + fontScale + "-" + ratio + ".png"))) {
      assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
    }
    MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
    button.dispatchTouchEvent(cancel);
    cancel.recycle();
    image.recycle();
  }

  private void layout(View view) {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, 393, 800);
  }
}
