package com.gameaicoach

import org.json.JSONObject

object CoachEngine {
 fun ai(server:JSONObject?)=server?.optJSONObject("ai")
 fun connected(server:JSONObject?)=ai(server)?.optBoolean("connected",false)==true
 fun headline(server:JSONObject?)=ai(server)?.optString("headline")?.trim().orEmpty()
 fun summary(server:JSONObject?)=ai(server)?.optString("accountSummary")?.trim().orEmpty()
 fun provider(server:JSONObject?)=ai(server)?.optString("provider")?.trim().orEmpty()
 fun confidence(server:JSONObject?)=ai(server)?.optInt("confidence",0)?:0

 fun plan(server:JSONObject?):List<JSONObject>{
  val a=ai(server)?.optJSONArray("planToday")?:return emptyList()
  return (0 until minOf(a.length(),7)).mapNotNull{a.optJSONObject(it)}
 }
 fun upgrades(server:JSONObject?):List<JSONObject>{
  val a=ai(server)?.optJSONArray("upgrades")?:return emptyList()
  return (0 until minOf(a.length(),8)).mapNotNull{a.optJSONObject(it)}
 }
 fun teams(server:JSONObject?):List<JSONObject>{
  val a=ai(server)?.optJSONArray("teams")?:return emptyList()
  return (0 until minOf(a.length(),6)).mapNotNull{a.optJSONObject(it)}
 }
 fun units(server:JSONObject?):List<JSONObject>{
  val a=ai(server)?.optJSONArray("units")?:return emptyList()
  return (0 until minOf(a.length(),18)).mapNotNull{a.optJSONObject(it)}
 }
 fun resources(server:JSONObject?):List<JSONObject>{
  val a=ai(server)?.optJSONArray("resources")?:return emptyList()
  return (0 until minOf(a.length(),10)).mapNotNull{a.optJSONObject(it)}
 }
 fun objects(server:JSONObject?,key:String,max:Int=8):List<JSONObject>{
  val a=ai(server)?.optJSONArray(key)?:return emptyList()
  return (0 until minOf(a.length(),max)).mapNotNull{a.optJSONObject(it)}
 }
 fun strings(server:JSONObject?,key:String,max:Int=8):List<String>{
  val a=ai(server)?.optJSONArray(key)?:return emptyList()
  return (0 until minOf(a.length(),max)).mapNotNull{a.optString(it).trim().takeIf(String::isNotBlank)}
 }
 fun coverage(summary:JSONObject?)="${summary?.optInt("coveragePercent",0)?:0}%"
}
