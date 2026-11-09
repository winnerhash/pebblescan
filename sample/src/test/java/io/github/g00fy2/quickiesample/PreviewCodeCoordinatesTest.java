package io.github.g00fy2.quickiesample;

import android.graphics.Matrix;
import android.util.Size;
import androidx.camera.view.TransformExperimental;
import androidx.camera.view.transform.OutputTransform;
import io.github.g00fy2.quickie.CodePosition;
import io.github.g00fy2.quickie.PreviewCodeCoordinates;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
@androidx.annotation.OptIn(markerClass = {TransformExperimental.class})
public class PreviewCodeCoordinatesTest {
  @Test
  public void portraitFillCropMapsAnalysisPixelsToTheDisplayedCode() {
    CodePosition mapped = coordinates(2160, 3840, 393, 800, false).map(new CodePosition(.25f, .4f, .1f, .1f));
    assertEquals(84f, mapped.getX() * 393, .01f);
    assertEquals(320f, mapped.getY() * 800, .01f);
  }

  @Test
  public void frontCameraMirroringFollowsThePreviewInsteadOfTheLayoutDirection() {
    CodePosition mapped = coordinates(2160, 3840, 393, 800, true).map(new CodePosition(.25f, .4f, .1f, .1f));
    assertEquals(309f, mapped.getX() * 393, .01f);
    assertEquals(320f, mapped.getY() * 800, .01f);
  }

  @Test
  public void landscapeCropAndDifferentAnalysisResolutionsGiveTheSameVisibleMarker() {
    CodePosition input = new CodePosition(.2f, .25f, .1f, .1f);
    CodePosition full = coordinates(3840, 2160, 800, 393, false).map(input);
    CodePosition small = coordinates(1920, 1080, 800, 393, false).map(input);
    assertEquals(160f, full.getX() * 800, .01f);
    assertEquals(84f, full.getY() * 393, .01f);
    assertEquals(full.getX(), small.getX(), .0001f);
    assertEquals(full.getY(), small.getY(), .0001f);
  }

  private PreviewCodeCoordinates coordinates(int width, int height, int viewWidth, int viewHeight, boolean mirror) {
    Matrix source = new Matrix();
    source.setScale(width / 2f, height / 2f);
    source.postTranslate(width / 2f, height / 2f);
    float scale = Math.max((float) viewWidth / width, (float) viewHeight / height);
    Matrix target = new Matrix();
    target.setScale(width * scale / 2f, height * scale / 2f);
    target.postTranslate(viewWidth / 2f, viewHeight / 2f);
    if (mirror) target.postScale(-1f, 1f, viewWidth / 2f, viewHeight / 2f);
    Size viewport = new Size(width, height);
    return new PreviewCodeCoordinates(
        new OutputTransform(source, viewport), new OutputTransform(target, viewport),
        width, height, viewWidth, viewHeight);
  }
}
