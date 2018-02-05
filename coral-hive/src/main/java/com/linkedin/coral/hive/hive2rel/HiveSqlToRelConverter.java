package com.linkedin.coral.hive.hive2rel;

import com.linkedin.coral.hive.hive2rel.rel.HiveUncollect;
import java.util.ArrayList;
import java.util.List;
import org.apache.calcite.linq4j.Ord;
import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.prepare.Prepare;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Uncollect;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlUnnestOperator;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql2rel.SqlRexConvertletTable;
import org.apache.calcite.sql2rel.SqlToRelConverter;


/**
 * Class to convert Hive SQL to Calcite RelNode. This class
 * specializes the functionality provided by {@link SqlToRelConverter}.
 */
class HiveSqlToRelConverter extends SqlToRelConverter {

  HiveSqlToRelConverter(RelOptTable.ViewExpander viewExpander, SqlValidator validator,
      Prepare.CatalogReader catalogReader, RelOptCluster cluster, SqlRexConvertletTable convertletTable,
      Config config) {
    super(viewExpander, validator, catalogReader, cluster, convertletTable, config);
  }

  @Override
  protected void convertFrom(Blackboard bb, SqlNode from) {
    if (from == null) {
      super.convertFrom(bb, from);
      return;
    }
    switch (from.getKind()) {
      case UNNEST:
        convertUnnestFrom(bb, from);
        break;
      default:
        super.convertFrom(bb, from);
        break;
    }
  }

  private void convertUnnestFrom(Blackboard bb, SqlNode from) {
    final SqlCall call;
    final SqlNode[] operands;
    call = (SqlCall) from;
    final List<SqlNode> nodes = call.getOperandList();
    final SqlUnnestOperator operator = (SqlUnnestOperator) call.getOperator();
    // FIXME: base class calls 'replaceSubqueries for operands here but that's a private
    // method. This is not an issue for our usecases with hive but we may need handling in future
    final List<RexNode> exprs = new ArrayList<>();
    final List<String> fieldNames = new ArrayList<>();
    for (Ord<SqlNode> node : Ord.zip(nodes)) {
      exprs.add(bb.convertExpression(node.e));
      fieldNames.add(validator.deriveAlias(node.e, node.i));
    }
    final RelNode input =
        RelOptUtil.createProject((null != bb.root) ? bb.root
                : LogicalValues.createOneRow(cluster), exprs, fieldNames,
            true);
    Uncollect uncollect = new HiveUncollect(cluster, cluster.traitSetOf(Convention.NONE), input, operator.withOrdinality);
    bb.setRoot(uncollect, true);
  }
}