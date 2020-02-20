package tool.analyzers.strategies;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import expressionsolver.Expression;
import expressionsolver.ExpressionSolver;
import fdtmc.FDTMC;
import fdtmc.State;
import jadd.ADD;
import jadd.JADD;
import paramwrapper.ParametricModelChecker;
import tool.CyclicRdgException;
import tool.RDGNode;
import tool.UnknownFeatureException;
import tool.analyzers.IReliabilityAnalysisResults;
import tool.analyzers.buildingblocks.Component;
import tool.analyzers.buildingblocks.ConcurrencyStrategy;
import tool.analyzers.buildingblocks.DerivationFunction;
import tool.analyzers.buildingblocks.FamilyBasedHelper;
import tool.analyzers.buildingblocks.IfOperator;
import tool.analyzers.buildingblocks.PresenceConditions;
import tool.analyzers.buildingblocks.ProductIterationHelper;
import tool.stats.IFormulaCollector;
import tool.stats.ITimeCollector;

public class FeatureFamilyProduct {
	
	private ExpressionSolver expressionSolver;
	private ADD featureModel;
	private ParametricModelChecker modelChecker;
	private ITimeCollector timeCollector;
	private IFormulaCollector formulaCollector;
	private FeatureBasedFirstPhase firstPhase;
	private FamilyBasedHelper helper;
	private DerivationFunction<Boolean, String, Double> solve;
	private DerivationFunction<Boolean, FDTMC, FDTMC> derive;

	public FeatureFamilyProduct (
			JADD jadd,
            ADD featureModel,
            ParametricModelChecker modelChecker,
            ITimeCollector timeCollector,
            IFormulaCollector formulaCollector		
	) {
		 	this.expressionSolver = new ExpressionSolver(jadd);
	        this.featureModel = featureModel;
	        this.modelChecker = modelChecker;

	        this.timeCollector = timeCollector;
	        this.formulaCollector = formulaCollector;

//	        this.firstPhase = new FamilyBasedFirstPhase(modelChecker, formulaCollector);
	        this.firstPhase = new FeatureBasedFirstPhase(modelChecker,
                    formulaCollector);
	        this.helper = new FamilyBasedHelper(expressionSolver);
	        
	        derive = DerivationFunction.abstractDerivation(new IfOperator<FDTMC>(),
                    FDTMC::inline,
                    trivialFdtmc());
	}

	public IReliabilityAnalysisResults evaluateReliability(RDGNode node, ConcurrencyStrategy concurrencyStrategy, Stream<Collection<String>> configurations) throws CyclicRdgException, UnknownFeatureException {
		
		List<RDGNode> dependencies = node.getDependenciesTransitiveClosure();
		List<String> presenceConditions = dependencies.stream()
				.map(RDGNode::getPresenceCondition)
                .collect(Collectors.toList());
		
		FDTMC model = node.getFDTMC();
//		System.out.println(model);
		 List<Component<String>> expressions = firstPhase.getReliabilityExpressions(dependencies, concurrencyStrategy);
//		 List<Component<String>> expressions = firstPhase.getReliabilityExpressions(dependencies, concurrencyStrategy);;
		Map<Collection<String>, Double> results = ProductIterationHelper.evaluate(configuration -> evaluateSingle(node,
                configuration,
	             dependencies),
				configurations,
				concurrencyStrategy);
		
//        
//        System.out.println(results);
		
		
		return null;
	}

	private Double evaluateSingle(RDGNode node, Collection<String> configuration, List<RDGNode> dependencies) throws UnknownFeatureException {
        List<Component<FDTMC>> models = RDGNode.toComponentList(dependencies);
        
        for(Component<FDTMC> model: models) {
        	System.out.println(model.getId());
        }
        // Lambda folding
        FDTMC rootModel = deriveFromMany(models, configuration);
        // Alpha
        String reliabilityExpression = modelChecker.getReliability(rootModel);
        formulaCollector.collectFormula(node, reliabilityExpression);
//        System.out.println(reliabilityExpression);
        // Sigma
        System.out.println(expressionSolver.solveExpression(reliabilityExpression));
        return expressionSolver.solveExpression(reliabilityExpression);
    }
	
    
    private FDTMC deriveFromMany(List<Component<FDTMC>> dependencies, Collection<String> configuration) {
        return Component.deriveFromMany(dependencies,
                                        derive,
                                        c -> PresenceConditions.isPresent(c.getPresenceCondition(),
                                                                          configuration,
                                                                          expressionSolver));
    }
    
    private FDTMC trivialFdtmc() {
        FDTMC trivial = new FDTMC();
        trivial.setVariableName("t");

        State initial = trivial.createInitialState();
        State success = trivial.createSuccessState();
        trivial.createTransition(initial, success, "", "1.0");

        return trivial;
    }


}
