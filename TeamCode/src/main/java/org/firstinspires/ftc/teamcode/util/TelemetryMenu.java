package org.firstinspires.ftc.teamcode.util;

/*
 * Copyright (c) 2023 OpenFTC Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import com.qualcomm.robotcore.hardware.Gamepad;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.ArrayList;
import java.util.Stack;

/**
 * TelemetryMenu — dpad-navigated menu drawn to the Driver Hub via HTML telemetry.
 *
 * Provides typed option elements (Enum, Integer, Boolean, StaticItem, submenus) and handles rising-
 * edge detection for gamepad navigation. Used to build a pre-match selection menu without needing a
 * laptop connected — the driver picks alliance, start pose, and delay right on the Driver Hub.
 *
 * Ported verbatim from FTC 5327 SMS Robotics' decode-2025 (common/utility/TelemetryMenu.java),
 * which is itself the OpenFTC Team's utility. Kept the OpenFTC license header above.
 *
 * NAV CONTROLS (shown on the Driver Hub):
 *   dpad up/down    navigate items
 *   X or Square     select
 *   dpad left/right edit option
 *   left bumper     up one level
 */
public class TelemetryMenu {
    private final MenuElement root;
    private final Telemetry telemetry;
    private MenuElement currentLevel;
    private boolean dpadUpPrev;
    private boolean dpadDnPrev;
    private boolean dpadRightPrev;
    private boolean dpadLeftPrev;
    private boolean xPrev;
    private boolean lbPrev;
    private int selectedIdx = 0;
    private final Stack<Integer> selectedIdxStack = new Stack<>();

    public TelemetryMenu(Telemetry telemetry, MenuElement root) {
        this.root = root;
        this.currentLevel = root;
        this.telemetry = telemetry;
        telemetry.setDisplayFormat(Telemetry.DisplayFormat.HTML);
        telemetry.setMsTransmissionInterval(50);
    }

    /** Call this from init_loop / initialize each cycle: reads gamepad, renders menu to telemetry. */
    public void loop(Gamepad gamepad) {
        boolean dpadUp = gamepad.dpad_up;
        boolean dpadDn = gamepad.dpad_down;
        boolean dpadRight = gamepad.dpad_right;
        boolean dpadLeft = gamepad.dpad_left;
        boolean x = gamepad.x;
        boolean lb = gamepad.left_bumper;

        ArrayList<Element> children = currentLevel.children();
        Element currentSelection = children.get(selectedIdx);

        if (currentSelection instanceof OptionElement) {
            if (dpadRight && !dpadRightPrev) {
                ((OptionElement) currentSelection).onRightInput();
            } else if (dpadLeft && !dpadLeftPrev) {
                ((OptionElement) currentSelection).onLeftInput();
            }
        }

        if (dpadUp && !dpadUpPrev) {
            selectedIdx--;
        } else if (dpadDn && !dpadDnPrev) {
            selectedIdx++;
        }

        if (selectedIdx >= children.size()) {
            selectedIdx = children.size() - 1;
        } else if (selectedIdx < 0) {
            selectedIdx = 0;
        } else if (x && !xPrev) {
            if (currentSelection instanceof SpecialUpElement) {
                if (currentLevel != root) {
                    selectedIdx = selectedIdxStack.pop();
                    currentLevel = currentLevel.parent();
                }
            } else if (currentSelection instanceof OptionElement) {
                ((OptionElement) currentSelection).onClick();
            } else if (currentSelection instanceof MenuElement) {
                selectedIdxStack.push(selectedIdx);
                selectedIdx = 0;
                currentLevel = (MenuElement) currentSelection;
            }
        } else if (lb && !lbPrev) {
            if (currentLevel != root) {
                selectedIdx = selectedIdxStack.pop();
                currentLevel = currentLevel.parent();
            }
        }

        dpadUpPrev = dpadUp;
        dpadDnPrev = dpadDn;
        dpadRightPrev = dpadRight;
        dpadLeftPrev = dpadLeft;
        xPrev = x;
        lbPrev = lb;

        StringBuilder builder = new StringBuilder();
        builder.append("<font color='#119af5' face=monospace>");
        builder.append("Navigate items.....dpad up/down\n")
                .append("Select.............X or Square\n")
                .append("Edit option........dpad left/right\n")
                .append("Up one level.......left bumper\n");
        builder.append("</font>");
        builder.append("\n");

        builder.append("<font face=monospace>");
        builder.append("Current Menu: ").append(currentLevel.name).append("\n");

        for (int i = 0; i < children.size(); i++) {
            if (selectedIdx == i) {
                builder.append("[<font color=green face=monospace>•</font>] ");
            } else {
                builder.append("[ ] ");
            }
            Element e = children.get(i);
            if (e instanceof MenuElement) {
                builder.append("> ");
            }
            builder.append(e.getDisplayText());
            builder.append("\n");
        }
        builder.append("</font>");

        telemetry.addLine(builder.toString());
    }

    public static class MenuElement extends Element {
        private final String name;
        private final ArrayList<Element> children = new ArrayList<>();

        public MenuElement(String name, boolean isRoot) {
            this.name = name;
            if (!isRoot) {
                children.add(new SpecialUpElement());
            }
        }

        public void addChild(Element child) {
            child.setParent(this);
            children.add(child);
        }

        public void addChildren(Element[] children) {
            for (Element e : children) {
                e.setParent(this);
                this.children.add(e);
            }
        }

        @Override
        protected String getDisplayText() { return name; }

        private ArrayList<Element> children() { return children; }
    }

    public static abstract class OptionElement extends Element {
        public void onClick() { }
        protected void onLeftInput() { }
        protected void onRightInput() { }
    }

    public static class EnumOption extends OptionElement {
        protected int idx = 0;
        protected Enum<?>[] e;
        protected String name;

        public EnumOption(String name, Enum<?>[] e) {
            this.e = e;
            this.name = name;
        }

        public EnumOption(String name, Enum<?>[] e, Enum<?> def) {
            this(name, e);
            idx = def.ordinal();
        }

        @Override
        public void onLeftInput() {
            idx++;
            if (idx > e.length - 1) idx = 0;
        }

        @Override
        public void onRightInput() {
            idx--;
            if (idx < 0) idx = e.length - 1;
        }

        @Override
        public void onClick() { }

        @Override
        protected String getDisplayText() {
            return String.format("%s: <font color='#e37c07' face=monospace>%s</font>", name, e[idx].name());
        }

        public Enum<?> getValue() { return e[idx]; }
    }

    public static class IntegerOption extends OptionElement {
        protected int i;
        protected int min;
        protected int max;
        protected String name;

        public IntegerOption(String name, int min, int max, int def) {
            this.name = name;
            this.min = min;
            this.max = max;
            this.i = def;
        }

        @Override
        public void onLeftInput() {
            i--;
            if (i < min) i = max;
        }

        @Override
        public void onRightInput() {
            i++;
            if (i > max) i = min;
        }

        @Override
        public void onClick() { }

        @Override
        protected String getDisplayText() {
            return String.format("%s: <font color='#e37c07' face=monospace>%d</font>", name, i);
        }

        public int getValue() { return i; }
    }

    public static class BooleanOption extends OptionElement {
        private final String name;
        private boolean val;
        private String customTrue;
        private String customFalse;

        public BooleanOption(String name, boolean def) {
            this.name = name;
            this.val = def;
        }

        public BooleanOption(String name, boolean def, String customTrue, String customFalse) {
            this(name, def);
            this.customTrue = customTrue;
            this.customFalse = customFalse;
        }

        @Override public void onLeftInput() { val = !val; }
        @Override public void onRightInput() { val = !val; }
        @Override public void onClick() { val = !val; }

        @Override
        protected String getDisplayText() {
            String valStr;
            if (customTrue != null && customFalse != null) {
                valStr = val ? customTrue : customFalse;
            } else {
                valStr = val ? "true" : "false";
            }
            return String.format("%s: <font color='#e37c07' face=monospace>%s</font>", name, valStr);
        }

        public boolean getValue() { return val; }
    }

    public static class StaticItem extends OptionElement {
        private final String name;
        public StaticItem(String name) { this.name = name; }
        @Override protected String getDisplayText() { return name; }
    }

    public static abstract class Element {
        private MenuElement parent;
        protected void setParent(MenuElement parent) { this.parent = parent; }
        protected MenuElement parent() { return parent; }
        protected abstract String getDisplayText();
    }

    private static class SpecialUpElement extends Element {
        @Override
        protected String getDisplayText() {
            return "<font color='#119af5' face=monospace>.. ↰ Up One Level</font>";
        }
    }
}
