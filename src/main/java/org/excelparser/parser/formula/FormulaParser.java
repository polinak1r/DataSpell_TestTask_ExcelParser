package org.excelparser.parser.formula;

import org.excelparser.parser.token.Token;
import org.excelparser.parser.token.TokenType;
import org.excelparser.parser.expression.*;
import org.excelparser.parser.expression.function.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class FormulaParser {
    private List<Token> tokens;

    private JTable table;

    public FormulaParser(List<Token> tokens, JTable table) {
        this.tokens = tokens;
        this.table = table;
    }

    /**
     * Splits the given list of tokens into sublists based on specified separators.
     *
     * @param tokensToSplit the list of tokens to split
     * @param separators    the characters used as separators for splitting
     * @return a list of lists of tokens, each representing a sub-expression
     * @throws IllegalArgumentException if the parentheses are mismatched
     */
    public List<List<Token>> split(List<Token> tokensToSplit, char[] separators) {
        List<List<Token>> expressions = new ArrayList<>();
        List<Token> currentExpressionTokens = new ArrayList<>();
        int openCount = 0;
        int closeCount = 0;

        for (Token currentToken : tokensToSplit) {
            if (isOperator(currentToken.getValue(), '(')) {
                openCount++;
            } else if (isOperator(currentToken.getValue(), ')')) {
                closeCount++;
                if (openCount < closeCount) {
                    throw new IllegalArgumentException("Wrong parenthesis");
                }
            }

            boolean isSeparator = false;
            for (char sep : separators) {
                if (isOperator(currentToken.getValue(), sep) && openCount == closeCount) {
                    expressions.add(new ArrayList<>(currentExpressionTokens));
                    currentExpressionTokens.clear();
                    isSeparator = true;
                    break;
                }
            }

            if (!isSeparator) {
                currentExpressionTokens.add(currentToken);
            }
        }

        if (openCount != closeCount) {
            throw new IllegalArgumentException("Wrong parenthesis");
        }

        if (!currentExpressionTokens.isEmpty()) {
            expressions.add(new ArrayList<>(currentExpressionTokens));
        }

        return expressions;
    }

    public Expression parse() {
        Expression result = parseExpression(tokens);
        return result;
    }

    /**
     * Parses a list of tokens into an expression, handling addition and subtraction.
     *
     * @param tokensToParse the list of tokens to parse
     * @return the parsed expression
     * @throws IllegalArgumentException if the expression is empty
     */
    public Expression parseExpression(List<Token> tokensToParse) {
        char[] separators = {'+', '-'};
        List<List<Token>> splitTokens = split(tokensToParse, separators);

        List<Expression> terms = new ArrayList<>();
        for (List<Token> tokenList : splitTokens) {
            if (tokenList.isEmpty()) {
                terms.add(new NumberExpression(0));
            } else {
                terms.add(parseTerm(tokenList));
            }
        }

        if (terms.isEmpty()) {
            throw new IllegalArgumentException("Empty expression");
        }

        Expression result = terms.get(0);
        int tokenIndex = 0;
        for (int i = 1; i < terms.size(); i++) {
            tokenIndex += splitTokens.get(i - 1).size();
            Token operatorToken = tokensToParse.get(tokenIndex);
            char operator = operatorToken.getValue().charAt(0);
            tokenIndex++;

            result = new BinaryExpression(result, terms.get(i), operator);
        }

        return result;
    }

    /**
     * Parses a list of tokens into a term, handling multiplication and division.
     *
     * @param tokensToParse the list of tokens to parse
     * @return the parsed expression
     */
    public Expression parseTerm(List<Token> tokensToParse) {
        char[] separators = {'*', '/'};
        List<List<Token>> splitTokens = split(tokensToParse, separators);

        List<Expression> factors = new ArrayList<>();
        for (List<Token> tokenList : splitTokens) {
            factors.add(parseFactor(tokenList));
        }

        Expression result = factors.get(0);
        int tokenIndex = 0;
        for (int i = 1; i < factors.size(); i++) {
            tokenIndex += splitTokens.get(i - 1).size();
            Token operatorToken = tokensToParse.get(tokenIndex);
            char operator = operatorToken.getValue().charAt(0);
            tokenIndex++;

            result = new BinaryExpression(result, factors.get(i), operator);
        }

        return result;
    }

    /**
     * Parses a list of tokens which can be a number, a cell reference, a function, or a parenthesis-enclosed expression.
     *
     * @param tokensToParse the list of tokens to parse
     * @return the parsed expression
     * @throws IllegalArgumentException if the token sequence is unexpected
     */
    private Expression parseFactor(List<Token> tokensToParse) {
        if (tokensToParse.size() == 1) {
            Token token = tokensToParse.get(0);
            return switch (token.getType()) {
                case NUMBER -> new NumberExpression(Double.parseDouble(token.getValue()));
                case CELL_REF -> parseCellReference(token.getValue());
                default -> throw new IllegalArgumentException("Unexpected token: " + token.getValue());
            };
        } else if (tokensToParse.get(0).getType() == TokenType.PARENTHESIS && tokensToParse.get(0).getValue().equals("(") &&
                tokensToParse.get(tokensToParse.size() - 1).getType() == TokenType.PARENTHESIS && tokensToParse.get(tokensToParse.size() - 1).getValue().equals(")")) {
            return parseExpression(tokensToParse.subList(1, tokensToParse.size() - 1));
        } else if (tokensToParse.get(0).getType() == TokenType.FUNCTION) {
            return parseFunction(tokensToParse);
        } else {
            throw new IllegalArgumentException("Unexpected token sequence: " + tokensToParse);
        }
    }

    /**
     * Parses a function call from a list of tokens.
     *
     * @param tokensToParse the list of tokens representing the function call
     * @return the parsed function expression
     * @throws IllegalArgumentException if the function is unknown or parentheses are mismatched
     */
    private Expression parseFunction(List<Token> tokensToParse) {
        Token functionToken = tokensToParse.get(0);
        String functionName = functionToken.getValue();
        List<Expression> arguments = new ArrayList<>();

        int openParenthesisIndex = tokensToParse.indexOf(new Token(TokenType.PARENTHESIS, "("));
        int closeParenthesisIndex = tokensToParse.lastIndexOf(new Token(TokenType.PARENTHESIS, ")"));
        if (openParenthesisIndex == -1 || closeParenthesisIndex == -1) {
            throw new IllegalArgumentException("Mismatched parentheses in function call: " + functionName);
        }

        List<Token> argumentsTokens = tokensToParse.subList(openParenthesisIndex + 1, closeParenthesisIndex);
        List<List<Token>> splitArgumentsTokens = split(argumentsTokens, new char[]{','});
        for (List<Token> argumentTokens : splitArgumentsTokens) {
            arguments.add(parseExpression(argumentTokens));
        }

        return switch (functionName.toLowerCase()) {
            case "sin" -> new SinExpression(arguments);
            case "cos" -> new CosExpression(arguments);
            case "pow" -> new PowExpression(arguments);
            case "max" -> new MaxExpression(arguments);
            case "min" -> new MinExpression(arguments);
            default -> throw new IllegalArgumentException("Unknown function: " + functionName);
        };
    }

    /**
     * Parses a cell reference into a NumberExpression.
     *
     * @param cellRef the cell reference in the form of "A1", "B2", etc.
     * @return the parsed cell reference as a NumberExpression
     * @throws IllegalArgumentException if the cell reference is invalid
     */
    private Expression parseCellReference(String cellRef) {
        int column = cellRef.charAt(0) - 'A' + 1;
        int row = Integer.parseInt(cellRef.substring(1)) - 1;
        if (row >= table.getRowCount()) {
            throw new IllegalArgumentException("Invalid cell reference: " + cellRef + ". The table has only " + table.getRowCount() + " rows.");
        }
        if (row <= -1) {
            throw new IllegalArgumentException("Invalid cell reference: " + cellRef + ". The row should be greater than zero.");
        }
        Object cellValue = table.getValueAt(row, column);
        if (table.getSelectedColumn() == column && table.getSelectedRow() == row) {
            throw new IllegalArgumentException("Cell can't reference to itself");
        }
        try {
            return new NumberExpression(Double.parseDouble(cellValue.toString()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cell reference does not contain a number");
        }
    }

    private boolean isOperator(String value, char operator) {
        return value.length() == 1 && value.charAt(0) == operator;
    }
}
