import React from "react";
import Consequence from "../Consequence";
import DatabaseTransactionsProcedure from "./procedures/DatabaseTransactionsProcedure";

function DatabaseTransactionsConsequence(props) {

  if (!props.show) {
    return null
  }

  return (
      <Consequence
          indicator="Database Transactions"
          color="red"
          problem="A transaction has been open for a long time or the transaction ID age is approaching its limit."
          acceptableRange={[
            "No transaction open for more than one hour",
            "Transaction ID age below one billion",
            "Red: open for more than six hours, or age above 1.5 billion"
          ]}
          consequences={[
            "Vacuum cannot remove dead rows in any table",
            "At an age of 2.1 billion the database refuses all writes"
          ]}
          procedure={<DatabaseTransactionsProcedure />}
      />
  )

}

export default DatabaseTransactionsConsequence;
