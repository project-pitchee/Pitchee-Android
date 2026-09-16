package io.rovly.pitchee.ui

internal data class ReadingPassage(
    val title: String,
    val text: String,
)

internal val readingPassages = listOf(
    ReadingPassage(
        title = "北风与太阳",
        text = "北风和太阳争论谁更有力量。路上来了一位行人，他们约定，谁能让行人脱下外套，谁就赢。北风用力吹，行人反而把外套裹得更紧。太阳温暖地照着，行人渐渐感到热，最后主动脱下了外套。",
    ),
    ReadingPassage(
        title = "乌鸦喝水",
        text = "一只乌鸦口渴了，到处找水喝。它看见一个瓶子，瓶子里有水，可是水位太低，乌鸦够不着。乌鸦衔来一颗颗小石子放进瓶里。瓶子里的水渐渐升高，乌鸦终于喝到了水。",
    ),
    ReadingPassage(
        title = "小马过河",
        text = "小马要把半袋麦子送到磨坊。一条小河挡住了路。牛说河水很浅，松鼠说河水很深。小马回家问妈妈。妈妈让它自己去试一试。小马小心地走进河里，发现河水既不像牛说的那么浅，也不像松鼠说的那么深。",
    ),
)
