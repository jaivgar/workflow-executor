package se.ltu.workflow.executor.state_machine;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

/* TODO:Further functionality is achieved if the logic expressions can be 
 * recursively composed of logic expressions
 */

/**
 * This class represents a valid logic expression to be tested in a State Machine
 * <p>
 * It wraps together a logic operator and a number of operands to form the logic
 * expression. <br>
 * An alternative to this class could be the use of the functional interface
 * {@link java.util.function.Predicate Predicate}, which offer methods for AND, OR and Logical
 * negation of a predicate that returns a boolean value.
 *
 * @param <E>  Type of logic operand grouped by this logic expression
 * @param <C>  Type of context used to evaluate the operands
 */
public class LogicExpression<E extends Evaluable<C>, C> {

	private final LogicOperator operator;
	private final Collection<E> operands;
	
	public LogicExpression(LogicOperator operator, Collection<E> operands) 
	        throws IllegalNumberOfOperandsException {
	    
	    testValidLogicExpression(operator, operands);
	    
		this.operator = operator;
		this.operands = operands;
	}
	
	/**
	 * Evaluates the logic expression to true or false, in a 2 step process.<br>
	 * First checks if each operand is true or false in this context, then
	 * test those boolean values with respect to the operator chosen.
	 * 
	 * @param context  The external context to evaluate each operand
	 * 
	 * @return True if the whole expression is true, after checking operands
	 * and operator.
	 * 
	 * @throws IllegalLogicExpressionException if the logic expression is
	 * missing the logic operator and has more than one operand
	 */
	public boolean evaluateLogicExpression(C context) throws IllegalLogicExpressionException {
		
		// No need to check for null, as is previously tested in update() method in StateMachine class
		/*if (operands == null) {
			return true;
		}
		*/
		List<Boolean> operandsEvaluated = new ArrayList<>();
		for(E e: operands){
			operandsEvaluated.add(e.evaluate(context));
		}
		boolean result;
		if (operator == null) {
			if (operands.size() != 1) {
				throw new IllegalLogicExpressionException(
				        "LogicExpression needs a LogicOperator if more than one operand");
			}
			else {
				result = operandsEvaluated.get(0);
			}
		}
		else {
				result = operator.evaluateOperator(
				        operandsEvaluated.toArray(new Boolean[operandsEvaluated.size()]));
		}
		return result;
	}
	
	/**
	 * Test each logic expression and creation time to verify that the combination of
	 * operands and operator is valid.
	 * 
	 * @param operator  The operator used to create this {@code LogicExpression}
     * @param operands  The operands used to create this {@code LogicExpression}
	 * 
	 * @throws IllegalNumberOfOperandsException  If the number of operands for that
	 * operator is invalid
	 */
	private void testValidLogicExpression(LogicOperator operator, Collection<E> operands)
	        throws IllegalNumberOfOperandsException {
	    if (operator == null) return;
	    operator.testValidOperator(operands.size());
	}
}
