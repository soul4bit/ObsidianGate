package ru.mcrpg.launcher.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

public final class LauncherIcons {

    private LauncherIcons() {
    }

    public static Node icon(String name, double size, String color) {
        IconSpec spec = resolveSpec(name);
        SVGPath path = new SVGPath();
        path.setContent(spec.path);

        Color resolvedColor = Color.web(color);
        if (spec.stroke) {
            path.setFill(Color.TRANSPARENT);
            path.setStroke(resolvedColor);
            path.setStrokeWidth(2.2d);
            path.setStrokeLineCap(StrokeLineCap.ROUND);
            path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        } else {
            path.setFill(resolvedColor);
        }

        double scale = size / 24.0d;
        path.getTransforms().add(new Scale(scale, scale, 12.0d, 12.0d));

        StackPane pane = new StackPane(path);
        pane.setMinSize(size, size);
        pane.setPrefSize(size, size);
        pane.setMaxSize(size, size);
        return pane;
    }

    public static Node logoCube(double size) {
        double scale = size / 44.0d;

        SVGPath shell = path("M22 4L37 12.7V30.1L22 38.8L7 30.1V12.7L22 4Z");
        shell.setFill(Color.web("#070912"));
        shell.setStroke(Color.web("#A855F7"));
        shell.setStrokeWidth(1.5d);

        SVGPath top = path("M22 4L37 12.7L22 21.4L7 12.7L22 4Z");
        top.setFill(new LinearGradient(
            13.0d, 8.0d, 28.0d, 35.0d, false, CycleMethod.NO_CYCLE,
            new Stop(0.0d, Color.web("#F0ABFC")),
            new Stop(1.0d, Color.web("#7C3AED"))
        ));
        top.setOpacity(0.9d);

        SVGPath left = path("M7 12.7L22 21.4V38.8L7 30.1V12.7Z");
        left.setFill(Color.web("#5B21B6"));
        left.setOpacity(0.88d);

        SVGPath right = path("M37 12.7L22 21.4V38.8L37 30.1V12.7Z");
        right.setFill(Color.web("#2E1065"));
        right.setOpacity(0.96d);

        SVGPath edges = path("M22 21.4V38.8M7 12.7L22 21.4L37 12.7");
        edges.setFill(Color.TRANSPARENT);
        edges.setStroke(Color.web("#C084FC"));
        edges.setStrokeWidth(1.2d);
        edges.setStrokeLineCap(StrokeLineCap.ROUND);
        edges.setStrokeLineJoin(StrokeLineJoin.ROUND);

        SVGPath topInset = path("M15.5 10.2L22 6.5L28.5 10.2L22 13.9L15.5 10.2Z");
        topInset.setFill(Color.web("#070912", 0.38d));

        SVGPath leftGlow = path("M11.5 17L17.2 20.3V26.9L11.5 23.6V17Z");
        leftGlow.setFill(Color.web("#D946EF", 0.4d));

        SVGPath rightGlow = path("M31.9 17L26.2 20.3V26.9L31.9 23.6V17Z");
        rightGlow.setFill(Color.web("#A855F7", 0.42d));

        Group group = new Group(shell, top, left, right, edges, topInset, leftGlow, rightGlow);
        group.getTransforms().add(new Scale(scale, scale, 22.0d, 22.0d));

        StackPane pane = new StackPane(group);
        pane.setMinSize(size, size);
        pane.setPrefSize(size, size);
        pane.setMaxSize(size, size);
        return pane;
    }

    private static IconSpec resolveSpec(String name) {
        return switch (name) {
            case "home" -> stroke("M3 10.5 12 3l9 7.5M5 10v10h5v-6h4v6h5V10");
            case "bell" -> stroke("M18 8C18 4.69 15.31 2 12 2C8.69 2 6 4.69 6 8C6 15 3 17 3 17H21C21 17 18 15 18 8Z M13.73 21C13.38 21.6 12.74 22 12 22C11.26 22 10.62 21.6 10.27 21");
            case "play" -> fill("M8 5v14l11-7z");
            case "check-circle" -> stroke("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM8.5 12.1l2.6 2.6 5.4-5.7");
            case "users" -> stroke("M8.2 10.4C10 10.4 11.4 9 11.4 7.2C11.4 5.4 10 4 8.2 4C6.4 4 5 5.4 5 7.2C5 9 6.4 10.4 8.2 10.4Z M2.8 17.6C3.25 14.8 5.3 13.1 8.2 13.1C11.1 13.1 13.15 14.8 13.6 17.6 M14.2 10.1C15.55 9.85 16.55 8.68 16.55 7.25C16.55 5.85 15.58 4.67 14.25 4.4 M15 13.35C17.05 13.85 18.35 15.3 18.75 17.6");
            case "signal" -> fill("M4 13.2H6.6V18H4V13.2ZM8.2 10.1H10.8V18H8.2V10.1ZM12.4 7.1H15V18H12.4V7.1ZM16.6 4H19.2V18H16.6V4Z");
            case "cube-small" -> stroke("M11 2.8L18.1 6.9V15.1L11 19.2L3.9 15.1V6.9L11 2.8Z M4.2 7.1L11 11L17.8 7.1 M11 11V18.8");
            case "refresh" -> stroke("M17.6 8.1C16.55 5.7 14.16 4 11.38 4C7.65 4 4.62 7.03 4.62 10.76C4.62 11.15 4.65 11.54 4.72 11.91 M18.1 4.9V8.2H14.8 M4.4 13.9C5.45 16.3 7.84 18 10.62 18C14.35 18 17.38 14.97 17.38 11.24C17.38 10.85 17.35 10.46 17.28 10.09 M3.9 17.1V13.8H7.2");
            case "profile" -> fill("M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-3.33 0-8 1.67-8 5v1h16v-1c0-3.33-4.67-5-8-5z");
            case "sync" -> stroke("M3 12a9 9 0 0 1 15-6.7L21 8M21 3v5h-5M21 12a9 9 0 0 1-15 6.7L3 16M3 21v-5h5");
            case "sword" -> stroke("M14.5 17.5 3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 13l2-2M5 14l5 5");
            case "server" -> stroke("M4 5h16v5H4zM4 14h16v5H4zM8 7h.01M8 16h.01");
            case "players" -> fill("M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zm6.5 1a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM2 20c0-3.3 3.6-6 7-6s7 2.7 7 6v1H2zm13.5-5.5c2.6.4 5.5 2.5 5.5 5.5v1h-3v-1c0-2.1-.9-4-2.5-5.5z");
            case "clock" -> stroke("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7v5l3 2");
            case "edit" -> stroke("M4 20h4.5L19 9.5 14.5 5 4 15.5V20zM13.5 6l4.5 4.5");
            case "folder" -> stroke("M3 6.5h6l2 2H21v9.5H3V6.5z");
            case "shield" -> stroke("M12 3l7 3v5.2c0 4.45-2.9 7.95-7 9.8-4.1-1.85-7-5.35-7-9.8V6l7-3zM9 12l2 2 4-4");
            case "info" -> stroke("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7h.01");
            case "crown" -> stroke("M4 8l4 4 4-7 4 7 4-4v10H4V8z");
            case "mail" -> stroke("M4 6h16v12H4V6zM4 7l8 6 8-6");
            case "id-card" -> stroke("M4 5h16v14H4V5zM8 10h4M8 14h8M15.5 10.5h1.5");
            case "bolt" -> fill("M13 2 4 14h6l-1 8 9-12h-6l1-8z");
            case "arrow-right" -> stroke("M5 12h14M13 6l6 6-6 6");
            case "external" -> stroke("M14 4h6v6M20 4l-9 9M20 14v5H5V4h5");
            case "copy" -> stroke("M8 8h11v11H8zM5 16H4a1 1 0 0 1-1-1V4h11v1");
            case "video" -> stroke("M4 7h11v10H4zM15 10.5l5-3v9l-5-3");
            case "settings" -> stroke("M12 15.25C13.7949 15.25 15.25 13.7949 15.25 12C15.25 10.2051 13.7949 8.75 12 8.75C10.2051 8.75 8.75 10.2051 8.75 12C8.75 13.7949 10.2051 15.25 12 15.25Z M19.02 13.52C19.08 13.03 19.08 12.97 19.08 12C19.08 11.03 19.08 10.97 19.02 10.48L21 8.94L18.95 5.39L16.61 6.33C15.82 5.73 15.63 5.62 14.71 5.25L14.36 2.75H9.64L9.29 5.25C8.37 5.62 8.18 5.73 7.39 6.33L5.05 5.39L3 8.94L4.98 10.48C4.92 10.97 4.92 11.03 4.92 12C4.92 12.97 4.92 13.03 4.98 13.52L3 15.06L5.05 18.61L7.39 17.67C8.18 18.27 8.37 18.38 9.29 18.75L9.64 21.25H14.36L14.71 18.75C15.63 18.38 15.82 18.27 16.61 17.67L18.95 18.61L21 15.06L19.02 13.52Z");
            case "discord" -> fill("M18.8 5.4C17.5 4.8 16.1 4.4 14.7 4.2L14.4 4.8C12.8 4.6 11.2 4.6 9.7 4.8L9.4 4.2C7.9 4.4 6.5 4.8 5.2 5.4C2.6 9.3 1.9 13.1 2.3 16.8C4.1 18.1 5.8 18.9 7.5 19.4L8.6 17.7C8 17.5 7.5 17.2 7 16.9L7.4 16.6C10.4 18 13.6 18 16.6 16.6L17 16.9C16.5 17.2 16 17.5 15.4 17.7L16.5 19.4C18.2 18.9 19.9 18.1 21.7 16.8C22.2 12.5 20.9 8.7 18.8 5.4ZM8.7 14.5C7.7 14.5 6.9 13.6 6.9 12.5C6.9 11.4 7.7 10.5 8.7 10.5C9.7 10.5 10.5 11.4 10.5 12.5C10.5 13.6 9.7 14.5 8.7 14.5ZM15.3 14.5C14.3 14.5 13.5 13.6 13.5 12.5C13.5 11.4 14.3 10.5 15.3 10.5C16.3 10.5 17.1 11.4 17.1 12.5C17.1 13.6 16.3 14.5 15.3 14.5Z");
            case "vk" -> fill("M3.5 7.2C3.6 12.9 6.5 16.3 11.7 16.3H12V13.1C13.9 13.3 15.3 14.6 15.9 16.3H19C18.2 13.8 16.3 12.4 15.1 11.9C16.3 11.2 18 9.5 18.4 7.2H15.6C15.1 9 13.5 10.7 12 10.9V7.2H9.2V13.6C7.6 13.2 5.7 11.4 5.6 7.2H3.5Z");
            case "globe" -> stroke("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3.6 9h16.8M3.6 15h16.8M12 3c2.4 2.5 3.6 5.5 3.6 9S14.4 18.5 12 21M12 3C9.6 5.5 8.4 8.5 8.4 12s1.2 6.5 3.6 9");
            case "github" -> fill("M12 2.2C6.6 2.2 2.2 6.6 2.2 12c0 4.3 2.8 8 6.7 9.3.5.1.7-.2.7-.5v-1.7c-2.7.6-3.3-1.1-3.3-1.1-.4-1.1-1.1-1.4-1.1-1.4-.9-.6.1-.6.1-.6 1 0 1.5 1 1.5 1 .9 1.5 2.3 1.1 2.9.8.1-.7.3-1.1.6-1.4-2.2-.2-4.5-1.1-4.5-4.8 0-1.1.4-1.9 1-2.6-.1-.2-.4-1.3.1-2.6 0 0 .8-.3 2.7 1 .8-.2 1.6-.3 2.4-.3.8 0 1.6.1 2.4.3 1.8-1.2 2.7-1 2.7-1 .5 1.3.2 2.4.1 2.6.6.7 1 1.5 1 2.6 0 3.7-2.3 4.5-4.5 4.8.4.3.7.9.7 1.8v2.6c0 .3.2.6.7.5 3.9-1.3 6.7-5 6.7-9.3 0-5.4-4.4-9.8-9.8-9.8z");
            case "trash" -> stroke("M3 6h18M8 6V4h8v2M6 6l1 15h10l1-15M10 10v7M14 10v7");
            case "download" -> stroke("M12 3v12M7 10l5 5 5-5M5 21h14");
            case "logout" -> stroke("M10 17l5-5-5-5M15 12H3M21 4v16h-8");
            case "chevron-left" -> stroke("M15 18l-6-6 6-6");
            case "chevron-right" -> stroke("M9 6l6 6-6 6");
            case "chevron-up" -> stroke("M18 15l-6-6-6 6");
            case "chevron-down" -> stroke("M6 9l6 6 6-6");
            case "x" -> stroke("M7 7l10 10M17 7 7 17");
            case "window-minimize" -> stroke("M6 12h12");
            case "window-close" -> stroke("M7 7l10 10M17 7 7 17");
            default -> fill("M4 4h16v16H4z");
        };
    }

    private static IconSpec fill(String path) {
        return new IconSpec(path, false);
    }

    private static IconSpec stroke(String path) {
        return new IconSpec(path, true);
    }

    private static SVGPath path(String content) {
        SVGPath path = new SVGPath();
        path.setContent(content);
        return path;
    }

    private static final class IconSpec {
        private final String path;
        private final boolean stroke;

        private IconSpec(String path, boolean stroke) {
            this.path = path;
            this.stroke = stroke;
        }
    }
}
