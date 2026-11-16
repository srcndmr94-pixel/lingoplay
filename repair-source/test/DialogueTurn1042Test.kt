fun main(){
    val dialogTurn = Regex("(?<=[.!?…\\]])\\s*[-–]\\s*(?=[\\[\\p{Lu}'\"])")
    val interruptedTurn = Regex("(?<=\\p{L})-\\s+-\\s*(?=[\\[\\p{Lu}'\"])")
 val x="I don't need to- -There's nothing we can do"
 val turns=x.split(interruptedTurn).flatMap{it.split(dialogTurn)}.map{it.trim()}
 check(turns==listOf("I don't need to", "There's nothing we can do")){turns}
 val y="Why didn't you? -I'm no killer."
 val t=y.split(interruptedTurn).flatMap{it.split(dialogTurn)}.map{it.trim()}
 check(t==listOf("Why didn't you?", "I'm no killer.")){t}
 val z="There is a co-worker-- a friend."
 check(z.split(interruptedTurn).flatMap{it.split(dialogTurn)}==listOf(z))
 println("3/3 genuine two-turn splitting tests PASS")
}
