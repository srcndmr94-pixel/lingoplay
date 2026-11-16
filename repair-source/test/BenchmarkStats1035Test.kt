import com.example.altyazi.BenchmarkStats1035
fun main() {
    check(BenchmarkStats1035.summarize(emptyList()).median == null)
    check(BenchmarkStats1035.summarize(listOf(350L)).median == 350L)
    check(BenchmarkStats1035.summarize(listOf(2L, 3L)).median == 2L)
    check(BenchmarkStats1035.summarize(listOf(9L, 4L, 6L)).median == 6L)
    check(BenchmarkStats1035.summarize(listOf(1200L, 100L, 700L, 350L)).median == 525L)
    check(BenchmarkStats1035.summarize(listOf(1200L, 100L, 700L, 350L)).p90 == 1200L)
    check(BenchmarkStats1035.summarize(listOf(1200L, 100L, 700L, 350L)).under500 == 2)
    check(BenchmarkStats1035.summarize((1L..10L).toList()).p90 == 9L)
    println("8/8 BenchmarkStats1035 tests PASS")
}
