/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.javascript.checks;

import org.sonar.api.server.rule.RulesDefinition.SubCharacteristics;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.javascript.api.tree.Tree;
import org.sonar.plugins.javascript.api.tree.Tree.Kind;
import org.sonar.plugins.javascript.api.tree.expression.IdentifierTree;
import org.sonar.plugins.javascript.api.tree.expression.PairPropertyTree;
import org.sonar.plugins.javascript.api.visitors.DoubleDispatchVisitorCheck;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

@Rule(
  key = "S3498",
  name = "Object literal shorthand syntax should be used",
  priority = Priority.MINOR,
  tags = {Tags.CONVENTION, Tags.ES2015})
@SqaleSubCharacteristic(SubCharacteristics.READABILITY)
@SqaleConstantRemediation("1min")
public class ObjectLiteralShorthandCheck extends DoubleDispatchVisitorCheck {

  private static final String MESSAGE = "Use shorthand for %s \"%s\".";

  @Override
  public void visitPairProperty(PairPropertyTree tree) {
    if (tree.key().is(Kind.IDENTIFIER_NAME)) {
      String keyName = ((IdentifierTree) tree.key()).name();

      if (tree.value() instanceof IdentifierTree && ((IdentifierTree) tree.value()).name().equals(keyName)) {
        raiseIssue("property", keyName, tree.key());
      }

      if (tree.value().is(Kind.FUNCTION_EXPRESSION)) {
        raiseIssue("method", keyName, tree.key());
      }
    }

    super.visitPairProperty(tree);
  }

  private void raiseIssue(String kind, String keyName, Tree highlighted) {
    String message = String.format(MESSAGE, kind, keyName);
    newIssue(highlighted, message);
  }
}