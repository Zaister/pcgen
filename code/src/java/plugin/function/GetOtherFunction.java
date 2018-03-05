/*
 * Copyright 2016 (C) Tom Parker <thpr@users.sourceforge.net>
 * 
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation;
 * either version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with
 * this library; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package plugin.function;

import java.util.Arrays;
import java.util.Optional;

import pcgen.base.formula.base.DependencyManager;
import pcgen.base.formula.base.DynamicDependency;
import pcgen.base.formula.base.EvaluationManager;
import pcgen.base.formula.base.FormulaManager;
import pcgen.base.formula.base.FormulaSemantics;
import pcgen.base.formula.base.Function;
import pcgen.base.formula.base.LegalScope;
import pcgen.base.formula.base.ScopeInstance;
import pcgen.base.formula.base.ScopeInstanceFactory;
import pcgen.base.formula.base.TrainingStrategy;
import pcgen.base.formula.base.VarScoped;
import pcgen.base.formula.parse.ASTQuotString;
import pcgen.base.formula.parse.Node;
import pcgen.base.formula.visitor.DependencyVisitor;
import pcgen.base.formula.visitor.EvaluateVisitor;
import pcgen.base.formula.visitor.SemanticsVisitor;
import pcgen.base.formula.visitor.StaticVisitor;
import pcgen.base.util.FormatManager;
import pcgen.cdom.formula.ManagerKey;
import pcgen.cdom.formula.scope.PCGenScope;
import pcgen.rules.context.LoadContext;

public class GetOtherFunction implements Function
{

	@Override
	public String getFunctionName()
	{
		return "getOther";
	}

	@Override
	public Boolean isStatic(StaticVisitor visitor, Node[] args)
	{
		//3-args, but we know first one is static (scope name)
		return (Boolean) args[1].jjtAccept(visitor, null)
			&& (Boolean) args[2].jjtAccept(visitor, null);
	}

	@Override
	public FormatManager<?> allowArgs(SemanticsVisitor visitor, Node[] args,
		FormulaSemantics semantics)
	{
		int argCount = args.length;
		if (argCount != 3)
		{
			semantics.setInvalid("Function " + getFunctionName()
				+ " received incorrect # of arguments, expected: 3 got " + args.length
				+ ' ' + Arrays.asList(args));
			return null;
		}

		Node scopeNode = args[0];
		if (!(scopeNode instanceof ASTQuotString))
		{
			semantics.setInvalid("Parse Error: Invalid Scope Node: "
				+ scopeNode.getClass().getName() + " found in location requiring a"
				+ " Static String (first arg cannot be evaluated)");
			return null;
		}
		ASTQuotString qs = (ASTQuotString) scopeNode;
		String legalScopeName = qs.getText();
		FormulaManager formulaManager = semantics.get(FormulaSemantics.FMANAGER);
		PCGenScope legalScope = (PCGenScope) formulaManager.getScopeInstanceFactory()
			.getScope(legalScopeName);
		if (legalScope == null)
		{
			semantics.setInvalid("Parse Error: Invalid Scope Name: " + legalScopeName
				+ " was not a defined scope");
			return null;
		}
		FormatManager<?> formatManager;
		try
		{
			LoadContext context = semantics.get(ManagerKey.CONTEXT);
			formatManager = legalScope.getFormatManager(context);
		}
		catch (UnsupportedOperationException e)
		{
			semantics.setInvalid("Parse Error: Invalid Scope Name: " + legalScopeName
				+ " found in location requiring a deterministic scope");
			return null;
		}
		FormatManager<?> objectFormat = (FormatManager<?>) args[1].jjtAccept(visitor,
			semantics.getWith(FormulaSemantics.ASSERTED, Optional.of(formatManager)));
		if (!semantics.isValid())
		{
			return null;
		}
		if (!formatManager.equals(objectFormat))
		{
			semantics.setInvalid(
				"Parse Error: Invalid Object Format: " + objectFormat.getIdentifierType()
					+ " found in a getOther call that asserted "
					+ formatManager.getIdentifierType());
			return null;
		}
		if (VarScoped.class.isAssignableFrom(objectFormat.getManagedClass()))
		{
			return (FormatManager<?>) args[2].jjtAccept(visitor,
				semantics.getWith(FormulaSemantics.SCOPE, legalScope));
		}
		else
		{
			semantics.setInvalid("Parse Error: Invalid Object Format: " + objectFormat
				+ " is not capable of holding variables");
			return null;
		}
	}

	@Override
	public Object evaluate(EvaluateVisitor visitor, Node[] args,
		EvaluationManager manager)
	{
		String legalScopeName = ((ASTQuotString) args[0]).getText();
		FormulaManager formulaManager = manager.get(EvaluationManager.FMANAGER);
		PCGenScope legalScope = (PCGenScope) formulaManager.getScopeInstanceFactory()
			.getScope(legalScopeName);
		LoadContext context = manager.get(ManagerKey.CONTEXT);
		VarScoped vs = (VarScoped) args[1].jjtAccept(visitor,
			manager.getWith(EvaluationManager.ASSERTED,
				Optional.of(legalScope.getFormatManager(context))));
		FormulaManager fm = manager.get(EvaluationManager.FMANAGER);
		ScopeInstanceFactory siFactory = fm.getScopeInstanceFactory();
		ScopeInstance scopeInst = siFactory.get(vs.getLocalScopeName(), vs);
		//Rest of Equation
		return args[2].jjtAccept(visitor,
			manager.getWith(EvaluationManager.INSTANCE, scopeInst));
	}

	@Override
	public FormatManager<?> getDependencies(DependencyVisitor visitor,
		DependencyManager fdm, Node[] args)
	{
		String legalScopeName = ((ASTQuotString) args[0]).getText();
		TrainingStrategy ts = new TrainingStrategy();
		FormulaManager formulaManager = fdm.get(DependencyManager.FMANAGER);
		ScopeInstanceFactory scopeInstanceFactory =
				formulaManager.getScopeInstanceFactory();
		PCGenScope legalScope =
				(PCGenScope) scopeInstanceFactory.getScope(legalScopeName);
		LoadContext context = fdm.get(ManagerKey.CONTEXT);
		args[1].jjtAccept(visitor,
			fdm.getWith(DependencyManager.VARSTRATEGY, ts).getWith(
				DependencyManager.ASSERTED,
				Optional.of(legalScope.getFormatManager(context))));
		DynamicDependency dd = new DynamicDependency(ts.getControlVar(),
			LegalScope.getFullName(legalScope));
		fdm.get(DependencyManager.DYNAMIC).addDependency(dd);
		DependencyManager dynamic = fdm.getWith(DependencyManager.VARSTRATEGY, dd);
		dynamic = dynamic.getWith(DependencyManager.SCOPE, legalScope);
		//Rest of Equation
		return (FormatManager<?>) args[2].jjtAccept(visitor, dynamic);
	}
}