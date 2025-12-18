package com.project;

import java.awt.Robot;
import java.awt.event.KeyEvent;

public class Main {

    public static String message = "@hewwoyuki.";

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        System.out.println("You have 10 seconds to focus the target window...");
        Thread.sleep(5000); // time to focus the target window

        while (true) { // continuous typing
            typeString(robot, message);
            Thread.sleep(1000);
            pressTab(robot);
            Thread.sleep(1000);
            pressEnter(robot);

            // random cooldown between 1.5 to 3 seconds
            Thread.sleep(1000);
        }
    }

    static void typeString(Robot robot, String text) {
        for (char c : text.toCharArray()) {
            typeChar(robot, c);
            robot.delay(50); // typing speed
        }
    }

    static void typeChar(Robot robot, char c) {
        try {
            switch (c) {
                case '@':
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(KeyEvent.VK_2); // @ is Shift + 2
                    robot.keyRelease(KeyEvent.VK_2);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                    break;
                default:
                    boolean upperCase = Character.isUpperCase(c);
                    int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
                    if (keyCode == KeyEvent.VK_UNDEFINED) return;

                    if (upperCase) robot.keyPress(KeyEvent.VK_SHIFT);

                    robot.keyPress(keyCode);
                    robot.keyRelease(keyCode);

                    if (upperCase) robot.keyRelease(KeyEvent.VK_SHIFT);
                    break;
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Cannot type character: " + c);
        }
    }


    static void pressEnter(Robot robot) {
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
    }

    static void pressTab(Robot robot) {
        robot.keyPress(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_TAB);
    }
}
