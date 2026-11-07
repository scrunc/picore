package org.illustro.picore.dynvar;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * High-performance, zero-dependency arithmetic expression evaluator.
 * Handles +, -, *, /, %, ^, parentheses, and math functions (floor, ceil, round, min, max, abs).
 */
public final class MathEvaluator {

    private static final DecimalFormat FORMAT = new DecimalFormat("#.####", DecimalFormatSymbols.getInstance(Locale.US));

    private MathEvaluator() {}

    /**
     * Evaluates a mathematical string expression and returns the formatted result.
     * If evaluation fails, returns "0".
     */
    public static String eval(String expression) {
        if (expression == null || expression.isBlank()) return "0";
        try {
            double result = new Parser(expression.trim()).parse();
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return "0";
            }
            if (result == (long) result) {
                return String.valueOf((long) result);
            }
            synchronized (FORMAT) {
                return FORMAT.format(result);
            }
        } catch (Throwable t) {
            return "0";
        }
    }

    private static class Parser {
        private final String str;
        private int pos = -1;
        private int ch;

        Parser(String str) {
            this.str = str;
            nextChar();
        }

        private void nextChar() {
            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
        }

        private boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        double parse() {
            double x = parseExpression();
            if (pos < str.length()) {
                throw new RuntimeException("Unexpected char: " + (char) ch);
            }
            return x;
        }

        private double parseExpression() {
            double x = parseTerm();
            for (;;) {
                if (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        private double parseTerm() {
            double x = parseFactor();
            for (;;) {
                if (eat('*')) x *= parseFactor();
                else if (eat('/')) {
                    double divisor = parseFactor();
                    x = divisor == 0 ? 0 : x / divisor;
                } else if (eat('%')) {
                    double divisor = parseFactor();
                    x = divisor == 0 ? 0 : x % divisor;
                } else return x;
            }
        }

        private double parseFactor() {
            if (eat('+')) return +parseFactor();
            if (eat('-')) return -parseFactor();

            double x;
            int startPos = this.pos;
            if (eat('(')) {
                x = parseExpression();
                eat(')');
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                x = Double.parseDouble(str.substring(startPos, this.pos));
            } else if (ch >= 'a' && ch <= 'z') {
                while (ch >= 'a' && ch <= 'z') nextChar();
                String func = str.substring(startPos, this.pos).toLowerCase();
                eat('(');
                x = parseExpression();
                if (eat(',')) {
                    double arg2 = parseExpression();
                    eat(')');
                    x = switch (func) {
                        case "min" -> Math.min(x, arg2);
                        case "max" -> Math.max(x, arg2);
                        default -> x;
                    };
                } else {
                    eat(')');
                    x = switch (func) {
                        case "floor" -> Math.floor(x);
                        case "ceil" -> Math.ceil(x);
                        case "round" -> (double) Math.round(x);
                        case "abs" -> Math.abs(x);
                        case "sqrt" -> Math.sqrt(x);
                        default -> x;
                    };
                }
            } else {
                throw new RuntimeException("Unexpected token: " + (char) ch);
            }

            if (eat('^')) x = Math.pow(x, parseFactor());

            return x;
        }
    }
}

