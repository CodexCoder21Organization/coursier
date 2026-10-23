# HTTP list contract checked

Read RFC 9110 sections 5.2, 5.6.1.2 and 8.4 at https://www.rfc-editor.org/rfc/rfc9110.html . Content-Encoding is a list that permits zero members; receivers ignore reasonable empty members. Repeated field lines contribute to one ordered combined list. This supports A1 acceptance and the inherited A2 repeated-field probes; an empty member has no coding name and does not remove a nonempty name elsewhere in the same field.
