package ru.mcrpg.launcher.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.util.Duration;

public final class MicroInteractions {

    private static final String HOVER_INSTALLED_KEY = "obsidiangate.micro.hoverInstalled";
    private static final String HOVER_TIMELINE_KEY = "obsidiangate.micro.hoverTimeline";
    private static final Duration HOVER_DURATION = Duration.millis(150.0d);
    private static final Duration ENTRANCE_DURATION = Duration.millis(260.0d);
    private static final Duration STATUS_DURATION = Duration.millis(180.0d);

    private MicroInteractions() {
    }

    public static void installHoverLift(Node node) {
        installHoverLift(node, -2.0d, 1.01d);
    }

    public static void installHoverLift(Node node, double liftY, double scale) {
        if (node == null || Boolean.TRUE.equals(node.getProperties().get(HOVER_INSTALLED_KEY))) {
            return;
        }
        node.getProperties().put(HOVER_INSTALLED_KEY, Boolean.TRUE);
        node.hoverProperty().addListener((observable, oldValue, hovered) ->
            animateHover(node, hovered, liftY, scale)
        );
    }

    public static void playEntrance(Node... nodes) {
        if (nodes == null || nodes.length == 0) {
            return;
        }
        Platform.runLater(() -> {
            double delayMillis = 0.0d;
            for (Node node : nodes) {
                if (node == null) {
                    continue;
                }
                playEntrance(node, delayMillis);
                delayMillis += 45.0d;
            }
        });
    }

    public static void playStatusSwap(Node... nodes) {
        if (nodes == null) {
            return;
        }
        for (Node node : nodes) {
            if (node == null) {
                continue;
            }
            FadeTransition fade = new FadeTransition(STATUS_DURATION, node);
            fade.setFromValue(0.64d);
            fade.setToValue(1.0d);
            fade.play();
        }
    }

    private static void animateHover(Node node, boolean hovered, double liftY, double scale) {
        Timeline previous = (Timeline) node.getProperties().get(HOVER_TIMELINE_KEY);
        if (previous != null) {
            previous.stop();
        }
        Timeline timeline = new Timeline(new KeyFrame(
            HOVER_DURATION,
            new KeyValue(node.translateYProperty(), hovered ? liftY : 0.0d, Interpolator.EASE_BOTH),
            new KeyValue(node.scaleXProperty(), hovered ? scale : 1.0d, Interpolator.EASE_BOTH),
            new KeyValue(node.scaleYProperty(), hovered ? scale : 1.0d, Interpolator.EASE_BOTH)
        ));
        node.getProperties().put(HOVER_TIMELINE_KEY, timeline);
        timeline.setOnFinished(event -> node.getProperties().remove(HOVER_TIMELINE_KEY));
        timeline.play();
    }

    private static void playEntrance(Node node, double delayMillis) {
        node.setOpacity(0.0d);
        node.setTranslateY(10.0d);

        FadeTransition fade = new FadeTransition(ENTRANCE_DURATION, node);
        fade.setDelay(Duration.millis(delayMillis));
        fade.setFromValue(0.0d);
        fade.setToValue(1.0d);

        TranslateTransition slide = new TranslateTransition(ENTRANCE_DURATION, node);
        slide.setDelay(Duration.millis(delayMillis));
        slide.setFromY(10.0d);
        slide.setToY(0.0d);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition entrance = new ParallelTransition(fade, slide);
        entrance.play();
    }
}
