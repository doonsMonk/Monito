# Keep the key derivator untouched: byte-exact behaviour matters for backup compatibility
-keep class com.monito.expense.data.InsecureSHA1PRNGKeyDerivator { *; }
