"use client"

import type React from "react"
import { ThemeToggle } from "@/components/theme-toggle"

import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Alert, AlertDescription } from "@/components/ui/alert"
import {
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  AreaChart,
  Area,
} from "recharts"
import { Upload, Download, FileSpreadsheet, BarChart3, Users, Clock, AlertCircle, CheckCircle2 } from "lucide-react"

type ReportType = "muster-roll" | "attendance-summary" | "daily-work"

interface ReportData {
  reportMonth?: string
  sites?: any
  shiftCalculations?: any
}

export default function PunchSyncPro() {
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [selectedYear, setSelectedYear] = useState<string>("")
  const [selectedMonth, setSelectedMonth] = useState<string>("")
  const [selectedReportType, setSelectedReportType] = useState<ReportType>("muster-roll")
  const [isLoading, setIsLoading] = useState(false)
  const [reportData, setReportData] = useState<ReportData | null>(null)
  const [error, setError] = useState<string>("")

  const currentYear = new Date().getFullYear()
  const years = Array.from({ length: 5 }, (_, i) => currentYear - i)
  const months = [
    { value: "1", label: "January" },
    { value: "2", label: "February" },
    { value: "3", label: "March" },
    { value: "4", label: "April" },
    { value: "5", label: "May" },
    { value: "6", label: "June" },
    { value: "7", label: "July" },
    { value: "8", label: "August" },
    { value: "9", label: "September" },
    { value: "10", label: "October" },
    { value: "11", label: "November" },
    { value: "12", label: "December" },
  ]

  const reportTypes = [
    { value: "muster-roll", label: "Muster Roll", description: "Monthly attendance with daily status" },
    {
      value: "attendance-summary",
      label: "Attendance Summary",
      description: "Detailed summary with overtime calculations",
    },
    { value: "daily-work", label: "Daily Work", description: "Daily work reports with punch times" },
  ]

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file && file.type === "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") {
      setSelectedFile(file)
      setError("")
    } else {
      setError("Please select a valid Excel file (.xlsx)")
    }
  }

  const generateReport = async (format: "json" | "excel") => {
    if (!selectedFile || !selectedYear || !selectedMonth) {
      setError("Please select a file, year, and month")
      return
    }

    setIsLoading(true)
    setError("")

    try {
      const formData = new FormData()
      formData.append("file", selectedFile)
      formData.append("year", selectedYear)
      formData.append("month", selectedMonth)

      const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
      const endpoint = `${apiUrl}/api/reports/${selectedReportType}/${format}`
      const response = await fetch(endpoint, {
        method: "POST",
        body: formData,
      })

      if (!response.ok) {
        throw new Error(`Failed to generate report: ${response.statusText}`)
      }

      if (format === "json") {
        const jsonData = await response.json()
        setReportData(jsonData)
      } else {
        // Handle Excel download
        const blob = await response.blob()
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement("a")
        a.href = url
        a.download = `${selectedReportType}_${selectedMonth}_${selectedYear}.xlsx`
        document.body.appendChild(a)
        a.click()
        window.URL.revokeObjectURL(url)
        document.body.removeChild(a)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "An error occurred")
    } finally {
      setIsLoading(false)
    }
  }

  const renderCharts = () => {
    if (!reportData) return null

    // Handle Attendance Summary
    if (selectedReportType === "attendance-summary" && reportData.shiftCalculations) {
      const shiftData = reportData.shiftCalculations["8-Hour Shift"] || reportData.shiftCalculations["9-Hour Shift"]
      if (!shiftData || !shiftData.sites) return null

      const sites = shiftData.sites
      const summaries = shiftData.summaries

      // Site-wise summary data
      const siteSummaryData = Object.keys(summaries).map((siteName) => ({
        site: siteName.length > 12 ? siteName.substring(0, 12) + "..." : siteName,
        totalHours: parseFloat(summaries[siteName].totalHours) || 0,
        totalOvertime: parseFloat(summaries[siteName].totalOvertimeHours) || 0,
        totalDutyUnits: parseFloat(summaries[siteName].totalDutyUnits) || 0,
        totalFullDays: summaries[siteName].totalFullDays || 0,
        totalMissing: summaries[siteName].totalMissingDays || 0,
      }))

      // Employee overtime data
      const employeeOvertimeData = []
      Object.keys(sites).forEach((siteName) => {
        const employees = sites[siteName]
        employees.forEach((emp: any) => {
          employeeOvertimeData.push({
            name: emp.name.length > 15 ? emp.name.substring(0, 15) + "..." : emp.name,
            overtime: parseFloat(emp.overtimeHours) || 0,
            site: siteName,
            fullDays: emp.fullDays || 0,
            halfDays: emp.halfDays || 0,
          })
        })
      })

      // Sort by overtime descending
      employeeOvertimeData.sort((a, b) => b.overtime - a.overtime)

      return (
        <div className="space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg font-semibold">Site-wise Duty & Overtime</CardTitle>
              </CardHeader>
              <CardContent>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={siteSummaryData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="site" />
                    <YAxis />
                    <Tooltip />
                    <Bar dataKey="totalDutyUnits" fill="#10b981" name="Total Duty Units" />
                    <Bar dataKey="totalOvertime" fill="#f59e0b" name="Overtime Hours" />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-lg font-semibold">Site Performance Overview</CardTitle>
              </CardHeader>
              <CardContent>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={siteSummaryData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="site" />
                    <YAxis />
                    <Tooltip />
                    <Bar dataKey="totalFullDays" fill="#10b981" name="Full Days" />
                    <Bar dataKey="totalMissing" fill="#ef4444" name="Missing Days" />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-lg font-semibold">Top Overtime Performers</CardTitle>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={400}>
                <BarChart data={employeeOvertimeData.slice(0, 15)} layout="horizontal">
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis type="number" />
                  <YAxis dataKey="name" type="category" width={120} />
                  <Tooltip />
                  <Bar dataKey="overtime" fill="#f59e0b" />
                </BarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </div>
      )
    }

    // Handle Muster Roll
    if (selectedReportType === "muster-roll" && reportData.sites) {
      const sites = reportData.sites

      // Employee attendance data
      const employeeAttendanceData = []
      const statusDistribution = { Present: 0, Absent: 0, HalfDay: 0, Missing: 0, WeekOff: 0 }

      for (const siteName in sites) {
        const site = sites[siteName]
        if (site.employees) {
          site.employees.forEach((emp: any) => {
            employeeAttendanceData.push({
              name: emp.name.length > 15 ? emp.name.substring(0, 15) + "..." : emp.name,
              attendance: emp.totalAttendance,
              site: siteName,
            })

            // Count status distribution
            emp.dailyStatus.forEach((status: string) => {
              switch (status) {
                case "P":
                  statusDistribution.Present++
                  break
                case "A":
                  statusDistribution.Absent++
                  break
                case "H":
                  statusDistribution.HalfDay++
                  break
                case "M":
                  statusDistribution.Missing++
                  break
                case "WO":
                  statusDistribution.WeekOff++
                  break
              }
            })
          })
        }
      }

      const statusChartData = [
        { name: "Present", value: statusDistribution.Present, color: "#10b981" },
        { name: "Absent", value: statusDistribution.Absent, color: "#ef4444" },
        { name: "Half Day", value: statusDistribution.HalfDay, color: "#f59e0b" },
        { name: "Missing Punch", value: statusDistribution.Missing, color: "#8b5cf6" },
        { name: "Week Off", value: statusDistribution.WeekOff, color: "#6b7280" },
      ]

      // Site summary data
      const siteSummaryData = Object.keys(sites).map((siteName) => ({
        site: siteName.length > 12 ? siteName.substring(0, 12) + "..." : siteName,
        attendance: sites[siteName].summary?.totalSiteAttendance || 0,
        halfDays: sites[siteName].summary?.totalHalfDays || 0,
        missingPunches: sites[siteName].summary?.totalMissingPunches || 0,
      }))

      return (
        <div className="space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg font-semibold">Attendance Status Distribution</CardTitle>
              </CardHeader>
              <CardContent>
                <ResponsiveContainer width="100%" height={300}>
                  <PieChart>
                    <Pie
                      data={statusChartData}
                      cx="50%"
                      cy="50%"
                      innerRadius={60}
                      outerRadius={120}
                      paddingAngle={5}
                      dataKey="value"
                    >
                      {statusChartData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-lg font-semibold">Site-wise Attendance Summary</CardTitle>
              </CardHeader>
              <CardContent>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={siteSummaryData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="site" />
                    <YAxis />
                    <Tooltip />
                    <Bar dataKey="attendance" fill="#10b981" name="Total Attendance" />
                    <Bar dataKey="missingPunches" fill="#ef4444" name="Missing Punches" />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-lg font-semibold">Employee Attendance Overview</CardTitle>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={400}>
                <BarChart data={employeeAttendanceData.slice(0, 15)} layout="horizontal">
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis type="number" />
                  <YAxis dataKey="name" type="category" width={120} />
                  <Tooltip />
                  <Bar dataKey="attendance" fill="#10b981" />
                </BarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </div>
      )
    }

    // Handle Daily Work
    if (selectedReportType === "daily-work" && reportData.sites) {
      const sites = reportData.sites
      const employeeOvertimeData = []
      const siteDutyData = []
      const dailyTrendData = []

      for (const siteName in sites) {
        const site = sites[siteName]

        // Site duty totals
        if (site.grandTotals) {
          siteDutyData.push({
            site: siteName.length > 12 ? siteName.substring(0, 12) + "..." : siteName,
            duty: Number.parseFloat(site.grandTotals.duty) || 0,
            overtime: Number.parseFloat(site.grandTotals.overtime) || 0,
          })
        }

        // Employee overtime data
        if (site.overtimeSummary) {
          site.overtimeSummary.forEach((emp: any) => {
            employeeOvertimeData.push({
              name: emp.name.length > 15 ? emp.name.substring(0, 15) + "..." : emp.name,
              overtime: Number.parseFloat(emp.totalOvertime) || 0,
              site: siteName,
            })
          })
        }

        // Daily trend (sample from first few entries)
        if (site.dailyEntries && site.dailyEntries.length > 0) {
          const dailyHours = {}
          site.dailyEntries.slice(0, 31).forEach((entry: any) => {
            const date = new Date(entry.date).getDate()
            if (!dailyHours[date]) {
              dailyHours[date] = { day: date, hours: 0, count: 0 }
            }
            dailyHours[date].hours += Number.parseFloat(entry.duration) || 0
            dailyHours[date].count += 1
          })

          Object.values(dailyHours).forEach((day: any) => {
            dailyTrendData.push({
              day: day.day,
              avgHours: day.hours / day.count,
            })
          })
        }
      }

      // Sort data for better visualization
      employeeOvertimeData.sort((a, b) => b.overtime - a.overtime)
      dailyTrendData.sort((a, b) => a.day - b.day)

      return (
        <div className="space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg font-semibold">Site-wise Duty & Overtime</CardTitle>
              </CardHeader>
              <CardContent>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={siteDutyData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="site" />
                    <YAxis />
                    <Tooltip />
                    <Bar dataKey="duty" fill="#10b981" name="Total Duty Hours" />
                    <Bar dataKey="overtime" fill="#f59e0b" name="Overtime Hours" />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-lg font-semibold">Daily Work Hours Trend</CardTitle>
              </CardHeader>
              <CardContent>
                <ResponsiveContainer width="100%" height={300}>
                  <AreaChart data={dailyTrendData.slice(0, 31)}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="day" />
                    <YAxis />
                    <Tooltip />
                    <Area type="monotone" dataKey="avgHours" stroke="#10b981" fill="#10b981" fillOpacity={0.3} />
                  </AreaChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-lg font-semibold">Top Overtime Performers</CardTitle>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={400}>
                <BarChart data={employeeOvertimeData.slice(0, 15)} layout="horizontal">
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis type="number" />
                  <YAxis dataKey="name" type="category" width={120} />
                  <Tooltip />
                  <Bar dataKey="overtime" fill="#f59e0b" />
                </BarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </div>
      )
    }

    return null
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b bg-card">
        <div className="container mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 bg-primary rounded-lg flex items-center justify-center">
                <BarChart3 className="w-6 h-6 text-primary-foreground" />
              </div>
              <div>
                <h1 className="text-2xl font-bold text-foreground">PunchSync Pro</h1>
                <p className="text-sm text-muted-foreground">Attendance Management Dashboard</p>
              </div>
            </div>
            <div className="flex items-center space-x-3">
              <Badge variant="secondary" className="bg-accent text-accent-foreground">
                Professional Edition
              </Badge>
              <ThemeToggle />
            </div>
          </div>
        </div>
      </header>

      <main className="container mx-auto px-4 py-8">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Control Panel */}
          <div className="lg:col-span-1">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center space-x-2">
                  <Upload className="w-5 h-5" />
                  <span>Report Configuration</span>
                </CardTitle>
                <CardDescription>Upload your Excel punch data and configure report parameters</CardDescription>
              </CardHeader>
              <CardContent className="space-y-6">
                {/* File Upload */}
                <div className="space-y-2">
                  <Label htmlFor="file-upload">Excel File</Label>
                  <Input
                    id="file-upload"
                    type="file"
                    accept=".xlsx"
                    onChange={handleFileChange}
                    className="cursor-pointer"
                  />
                  {selectedFile && (
                    <div className="flex items-center space-x-2 text-sm text-muted-foreground">
                      <FileSpreadsheet className="w-4 h-4" />
                      <span>{selectedFile.name}</span>
                    </div>
                  )}
                </div>

                {/* Year Selection */}
                <div className="space-y-2">
                  <Label>Report Year</Label>
                  <Select value={selectedYear} onValueChange={setSelectedYear}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select year" />
                    </SelectTrigger>
                    <SelectContent>
                      {years.map((year) => (
                        <SelectItem key={year} value={year.toString()}>
                          {year}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {/* Month Selection */}
                <div className="space-y-2">
                  <Label>Report Month</Label>
                  <Select value={selectedMonth} onValueChange={setSelectedMonth}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select month" />
                    </SelectTrigger>
                    <SelectContent>
                      {months.map((month) => (
                        <SelectItem key={month.value} value={month.value}>
                          {month.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {/* Report Type Selection */}
                <div className="space-y-2">
                  <Label>Report Type</Label>
                  <Select
                    value={selectedReportType}
                    onValueChange={(value: ReportType) => setSelectedReportType(value)}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {reportTypes.map((type) => (
                        <SelectItem key={type.value} value={type.value}>
                          <div>
                            <div className="font-medium">{type.label}</div>
                            <div className="text-xs text-muted-foreground">{type.description}</div>
                          </div>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {/* Action Buttons */}
                <div className="space-y-3 pt-4">
                  <Button
                    onClick={() => generateReport("json")}
                    disabled={isLoading || !selectedFile || !selectedYear || !selectedMonth}
                    className="w-full"
                  >
                    {isLoading ? (
                      <>
                        <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin mr-2" />
                        Generating...
                      </>
                    ) : (
                      <>
                        <BarChart3 className="w-4 h-4 mr-2" />
                        Generate Analytics
                      </>
                    )}
                  </Button>

                  <Button
                    variant="outline"
                    onClick={() => generateReport("excel")}
                    disabled={isLoading || !selectedFile || !selectedYear || !selectedMonth}
                    className="w-full"
                  >
                    <Download className="w-4 h-4 mr-2" />
                    Download Excel
                  </Button>
                </div>

                {/* Error Display */}
                {error && (
                  <Alert variant="destructive">
                    <AlertCircle className="h-4 w-4" />
                    <AlertDescription>{error}</AlertDescription>
                  </Alert>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Analytics Panel */}
          <div className="lg:col-span-2">
            <Tabs defaultValue="overview" className="space-y-6">
              <TabsList className="grid w-full grid-cols-3">
                <TabsTrigger value="overview">Overview</TabsTrigger>
                <TabsTrigger value="analytics">Analytics</TabsTrigger>
                <TabsTrigger value="reports">Reports</TabsTrigger>
              </TabsList>

              <TabsContent value="overview" className="space-y-6">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <Card>
                    <CardContent className="p-6">
                      <div className="flex items-center space-x-2">
                        <Users className="w-8 h-8 text-primary" />
                        <div>
                          <p className="text-2xl font-bold">245</p>
                          <p className="text-sm text-muted-foreground">Total Employees</p>
                        </div>
                      </div>
                    </CardContent>
                  </Card>

                  <Card>
                    <CardContent className="p-6">
                      <div className="flex items-center space-x-2">
                        <Clock className="w-8 h-8 text-accent" />
                        <div>
                          <p className="text-2xl font-bold">87%</p>
                          <p className="text-sm text-muted-foreground">Attendance Rate</p>
                        </div>
                      </div>
                    </CardContent>
                  </Card>

                  <Card>
                    <CardContent className="p-6">
                      <div className="flex items-center space-x-2">
                        <CheckCircle2 className="w-8 h-8 text-primary" />
                        <div>
                          <p className="text-2xl font-bold">3</p>
                          <p className="text-sm text-muted-foreground">Active Sites</p>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                </div>

                {reportData && (
                  <Card>
                    <CardHeader>
                      <CardTitle>Report Summary</CardTitle>
                      <CardDescription>
                        Generated report for {reportData.reportMonth || "Selected Period"}
                      </CardDescription>
                    </CardHeader>
                    <CardContent>
                      <div className="space-y-4">
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium">Processing Status</span>
                          <Badge variant="default" className="bg-primary text-primary-foreground">
                            Complete
                          </Badge>
                        </div>
                        <Progress value={100} className="w-full" />
                        <p className="text-sm text-muted-foreground">
                          Report data has been successfully processed and is ready for analysis.
                        </p>
                      </div>
                    </CardContent>
                  </Card>
                )}
              </TabsContent>

              <TabsContent value="analytics" className="space-y-6">
                {reportData ? (
                  <>
                    <Card>
                      <CardHeader>
                        <CardTitle>Data Visualization</CardTitle>
                        <CardDescription>Interactive charts and analytics for your attendance data</CardDescription>
                      </CardHeader>
                      <CardContent>{renderCharts()}</CardContent>
                    </Card>
                  </>
                ) : (
                  <Card>
                    <CardContent className="p-12 text-center">
                      <BarChart3 className="w-16 h-16 text-muted-foreground mx-auto mb-4" />
                      <h3 className="text-lg font-semibold mb-2">No Data Available</h3>
                      <p className="text-muted-foreground mb-4">Generate a report to view analytics and charts</p>
                      <Button
                        onClick={() => generateReport("json")}
                        disabled={!selectedFile || !selectedYear || !selectedMonth}
                      >
                        Generate Analytics
                      </Button>
                    </CardContent>
                  </Card>
                )}
              </TabsContent>

              <TabsContent value="reports" className="space-y-6">
                <Card>
                  <CardHeader>
                    <CardTitle>Available Reports</CardTitle>
                    <CardDescription>Choose from different report types based on your needs</CardDescription>
                  </CardHeader>
                  <CardContent>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                      {reportTypes.map((type) => (
                        <Card
                          key={type.value}
                          className={`cursor-pointer transition-colors ${
                            selectedReportType === type.value ? "ring-2 ring-primary" : ""
                          }`}
                        >
                          <CardContent className="p-4">
                            <h4 className="font-semibold mb-2">{type.label}</h4>
                            <p className="text-sm text-muted-foreground mb-4">{type.description}</p>
                            <Button
                              variant={selectedReportType === type.value ? "default" : "outline"}
                              size="sm"
                              onClick={() => setSelectedReportType(type.value as ReportType)}
                            >
                              {selectedReportType === type.value ? "Selected" : "Select"}
                            </Button>
                          </CardContent>
                        </Card>
                      ))}
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>
            </Tabs>
          </div>
        </div>
      </main>
    </div>
  )
}
