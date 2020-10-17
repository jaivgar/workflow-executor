package se.ltu.workflow.executor.state_machine;

/**
 * This enumeration represents the basic logical operators that can be used to test
 * events and guards. 
 * <p>
 * The operators are wrapped with operands in {@link LogicExpression}, that
 * is used by events or guards. Because operators are not isolated entities,
 * they are relations between operands, or modifiers of operands like NOT.
 * <p>
 * At the moment the class supports the operators NOT, AND, OR and XOR.
 * 
 */
public enum LogicOperator {
	
	NOT{
		@Override
		public boolean evaluateOperator(final Boolean... operands) {
				return !operands[0];
		}
		
		@Override
        public void testValidOperator(int numberOperands) throws IllegalNumberOfOperandsException{
            if(numberOperands != 1)
            {
                throw new IllegalNumberOfOperandsException(
                        "The operator NOT only accepts as input one operand");
            }
        }
	},
	AND{
		@Override
		public boolean evaluateOperator(final Boolean... operands) {
			for (int i= 0; i < operands.length; i++) {
				if (operands[i] == false) {
					return false;
				}
			}
			return true;
		}
		
		@Override
        public void testValidOperator(int numberOperands) throws IllegalNumberOfOperandsException {
            if(numberOperands < 2)
            {
                throw new IllegalNumberOfOperandsException(
                        "The operator AND only accepts as input two or more operands");
            }
        }
	},
	OR{
		@Override
		public boolean evaluateOperator(final Boolean... operands) {
			for (int i= 0; i < operands.length; i++) {
				if (operands[i] == true) {
					return true;
				}
			}
			return false;
		}
		
		@Override
        public void testValidOperator(int numberOperands) throws IllegalNumberOfOperandsException {
		    if(numberOperands < 2)
            {
                throw new IllegalNumberOfOperandsException(
                        "The operator OR only accepts as input two or more operands");
            }
        }
	},
	XOR{
		@Override
		public boolean evaluateOperator(final Boolean... operands){
			return operands[0] != operands[1];
		}
		
		@Override
		public void testValidOperator(int numberOperands) throws IllegalNumberOfOperandsException {
            if(numberOperands != 2)
            {
                throw new IllegalNumberOfOperandsException(
                        "The operator XOR only accepts as input two operands");
            }
		}
	};
	
	/**
	 * Combines the boolean representation of the operands according to the operator chosen.
	 * 
	 * @param operands  The array of operands as boolean variables that will be used by the operators
	 */
	public abstract boolean evaluateOperator(final Boolean... operands);
	
	/**
	 * Test if the {@code LogicOperator} can be used to evaluate that number of operands
	 * 
	 * @param numberOperands  The number of operands to test again the {@code LogicOperator}
	 * 
	 * @throws IllegalNumberOfOperandsException  if the {@code LogicOperator} can not handle the
	 * introduced number of operands
	 */
	public abstract void testValidOperator(int numberOperands) throws IllegalNumberOfOperandsException;

}
