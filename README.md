# PunchSync Pro

A professional attendance management dashboard for processing Excel punch data and generating comprehensive reports.

## Features

* **Excel File Processing**: Upload and process attendance punch data from Excel files
* **Multiple Report Types**:
   * Muster Roll Reports: Monthly attendance with daily status tracking
   * Daily Work Reports: Detailed punch times and work duration analysis
   * Attendance Summary: Comprehensive attendance statistics with overtime calculations
* **Interactive Visualizations**: Charts and graphs for data analysis
* **Theme Support**: Light and dark mode toggle
* **Excel Downloads**: Generate and download processed reports

## Tech Stack

### Frontend
* **Next.js 14** - React framework
* **React** - JavaScript library
* **TypeScript** - Type-safe JavaScript
* **Tailwind CSS** - Utility-first CSS framework
* **Recharts** - Data visualization library
* **shadcn/ui** - Component library
* **next-themes** - Dark/light mode support

### Backend
* **Spring Boot 3.0.0** - Java framework
* **Java 21** - Programming language

## Backend Integration

This frontend connects to a Spring Boot backend running on port 8080 with the following endpoints:

* `POST /api/reports/muster-roll/excel` - Generate Muster Roll Excel
* `POST /api/reports/muster-roll/json` - Get Muster Roll JSON data
* `POST /api/reports/attendance-summary/excel` - Generate Attendance Summary Excel
* `POST /api/reports/attendance-summary/json` - Get Attendance Summary JSON data
* `POST /api/reports/daily-work/excel` - Generate Daily Work Excel
* `POST /api/reports/daily-work/json` - Get Daily Work JSON data

## Getting Started

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Pskuntal1248/PunchSync-new-.git
   cd PunchSync-new-
   ```

2. **Install dependencies**:
   ```bash
   npm install
   ```

3. **Start the development server**:
   ```bash
   npm run dev
   ```

4. **Make sure your Spring Boot backend is running on port 8080**

5. **Open http://localhost:3000 in your browser**

## Usage

1. Upload an Excel file containing punch data
2. Select the year and month for the report
3. Choose the report type (Muster Roll, Daily Work, or Attendance Summary)
4. View the generated charts and analytics
5. Download the processed Excel report

## Project Structure

```
├── app/
│   ├── globals.css
│   ├── layout.tsx
│   └── page.tsx
├── components/
│   ├── ui/
│   ├── theme-provider.tsx
│   ├── theme-toggle.tsx
│   └── footer.tsx
└── package.json
```

## Screenshots

[Add screenshots of your application here]

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/new-feature`)
3. Commit your changes (`git commit -am 'Add new feature'`)
4. Push to the branch (`git push origin feature/new-feature`)
5. Create a Pull Request

## License

This project is licensed under the [MIT License](LICENSE) - see the LICENSE file for details.

---

**Made By**  
**Parth Singh** - Full Stack Developer

- GitHub: [@Pskuntal1248](https://github.com/Pskuntal1248)
